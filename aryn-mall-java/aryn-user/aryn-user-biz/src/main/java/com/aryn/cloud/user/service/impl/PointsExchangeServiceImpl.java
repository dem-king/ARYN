package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.remote.RemoteCouponInfoService;
import com.aryn.cloud.promotion.api.remote.RemoteCouponUserService;
import com.aryn.cloud.promotion.api.vo.CouponTemplateInfoVO;
import com.aryn.cloud.user.api.entity.PointsExchange;
import com.aryn.cloud.user.api.entity.PointsExchangeRecord;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.enums.PointsSceneEnum;
import com.aryn.cloud.user.api.vo.PointsExchangeVO;
import com.aryn.cloud.user.mapper.PointsExchangeMapper;
import com.aryn.cloud.user.mapper.PointsExchangeRecordMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IPointsExchangeService;
import com.aryn.cloud.user.service.IPointsGrantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 积分兑券。
 *
 * <p>顺序固定为「先落记录 → 扣积分 → 发券」且都在本地事务内：发券是最后一步远程调用，
 * 其失败会连带扣积分与记录一起回滚，最坏情形是用户什么都没得到、可重试，不会出现
 * 「券已发但积分没扣」的反向超发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsExchangeServiceImpl extends ServiceImpl<PointsExchangeMapper, PointsExchange>
		implements IPointsExchangeService {

	private final PointsExchangeRecordMapper exchangeRecordMapper;

	private final UserInfoMapper userInfoMapper;

	private final IPointsGrantService pointsGrantService;

	@DubboReference
	private final RemoteCouponInfoService remoteCouponInfoService;

	@DubboReference
	private final RemoteCouponUserService remoteCouponUserService;

	@Override
	public List<PointsExchangeVO> listApplicable(String userId) {
		List<PointsExchange> exchanges = this.list(Wrappers.<PointsExchange>lambdaQuery()
			.eq(PointsExchange::getStatus, "0")
			.orderByAsc(PointsExchange::getSortOrder)
			.orderByDesc(PointsExchange::getCreateTime));
		if (exchanges.isEmpty()) {
			return List.of();
		}
		UserInfo userInfo = userInfoMapper.selectById(userId);
		int balance = userInfo == null || userInfo.getPoint() == null ? 0 : Math.max(userInfo.getPoint(), 0);
		Map<String, Integer> exchangedCountByExchange = countUserExchanges(userId);
		Map<String, CouponTemplateInfoVO> templates = loadTemplates(exchanges);
		return exchanges.stream().map(exchange -> toVO(exchange, templates.get(exchange.getCouponId()), balance,
				exchangedCountByExchange.getOrDefault(exchange.getId(), 0))).toList();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PointsExchangeRecord exchange(String userId, String exchangeId) {
		PointsExchange exchange = this.getById(exchangeId);
		if (exchange == null || !"0".equals(exchange.getStatus())) {
			throw new ArynBusinessException("兑换项不存在或已下架");
		}
		if (exchange.getPointsCost() == null || exchange.getPointsCost() <= 0) {
			throw new ArynBusinessException("兑换项配置不合法，请联系客服");
		}
		if (exchange.getPerUserLimit() != null && exchange.getPerUserLimit() > 0) {
			long exchanged = exchangeRecordMapper.selectCount(Wrappers.<PointsExchangeRecord>lambdaQuery()
				.eq(PointsExchangeRecord::getUserId, userId)
				.eq(PointsExchangeRecord::getExchangeId, exchangeId));
			if (exchanged >= exchange.getPerUserLimit()) {
				throw new ArynBusinessException("本项每人限兑 " + exchange.getPerUserLimit() + " 次，已达上限");
			}
		}

		PointsExchangeRecord record = new PointsExchangeRecord().setId(IdWorker.getIdStr())
			.setUserId(userId)
			.setExchangeId(exchange.getId())
			.setExchangeTitle(exchange.getTitle())
			.setPointsCost(exchange.getPointsCost())
			.setCouponId(exchange.getCouponId());
		exchangeRecordMapper.insert(record);

		// 扣积分：余额不足在账本侧抛「积分余额不足」并整体回滚；幂等键挂记录 ID
		pointsGrantService.consume(userId, exchange.getPointsCost(), PointsSceneEnum.POINTS_EXCHANGE,
				"POINTS_EXCHANGE:" + record.getId(), "积分兑换：" + exchange.getTitle());

		// 发券：sourceType=POINTS_EXCHANGE + sourceId=记录ID，重放幂等；库存不足抛错回滚
		boolean granted = remoteCouponUserService.grantPointsExchangeCoupon(exchange.getCouponId(), userId,
				record.getId());
		if (!granted) {
			throw new ArynBusinessException("优惠券发放失败，请稍后重试");
		}
		return record;
	}

	@Override
	public List<PointsExchange> listAll() {
		return this.list(Wrappers.<PointsExchange>lambdaQuery()
			.orderByAsc(PointsExchange::getSortOrder)
			.orderByDesc(PointsExchange::getCreateTime));
	}

	@Override
	public boolean saveExchange(PointsExchange exchange) {
		validate(exchange);
		if (!StringUtils.hasText(exchange.getId())) {
			exchange.setId(null);
			return this.save(exchange);
		}
		return this.updateById(exchange);
	}

	@Override
	public boolean removeExchange(String id) {
		if (!StringUtils.hasText(id)) {
			throw new ArynBusinessException("兑换项ID不能为空");
		}
		return this.removeById(id);
	}

	private void validate(PointsExchange exchange) {
		if (exchange == null || !StringUtils.hasText(exchange.getTitle())) {
			throw new ArynBusinessException("兑换项标题不能为空");
		}
		if (exchange.getPointsCost() == null || exchange.getPointsCost() <= 0) {
			throw new ArynBusinessException("兑换所需积分必须大于0");
		}
		if (!StringUtils.hasText(exchange.getCouponId())) {
			throw new ArynBusinessException("请选择优惠券模板");
		}
		if (exchange.getPerUserLimit() != null && exchange.getPerUserLimit() < 0) {
			throw new ArynBusinessException("每人限兑次数不能为负数");
		}
		if (!StringUtils.hasText(exchange.getStatus())) {
			exchange.setStatus("0");
		}
		else if (!"0".equals(exchange.getStatus()) && !"1".equals(exchange.getStatus())) {
			throw new ArynBusinessException("状态不合法");
		}
		CouponTemplateInfoVO template;
		try {
			template = remoteCouponInfoService.getTemplate(exchange.getCouponId());
		}
		catch (Exception e) {
			log.warn("积分兑换券模板校验失败 couponId={}", exchange.getCouponId(), e);
			throw new ArynBusinessException("优惠券模板校验失败，请稍后重试");
		}
		if (template == null) {
			throw new ArynBusinessException("优惠券模板不存在");
		}
	}

	private Map<String, Integer> countUserExchanges(String userId) {
		Map<String, Integer> counts = new HashMap<>();
		for (PointsExchangeRecord record : exchangeRecordMapper.selectList(
				Wrappers.<PointsExchangeRecord>lambdaQuery().eq(PointsExchangeRecord::getUserId, userId))) {
			counts.merge(record.getExchangeId(), 1, Integer::sum);
		}
		return counts;
	}

	private Map<String, CouponTemplateInfoVO> loadTemplates(List<PointsExchange> exchanges) {
		Map<String, CouponTemplateInfoVO> templates = new HashMap<>();
		for (String couponId : exchanges.stream().map(PointsExchange::getCouponId).filter(Objects::nonNull)
				.distinct().toList()) {
			try {
				CouponTemplateInfoVO template = remoteCouponInfoService.getTemplate(couponId);
				if (template != null) {
					templates.put(couponId, template);
				}
			}
			catch (Exception e) {
				// 券模板查询失败按缺失处理：卡片降级展示标题，不影响列表
				log.warn("积分兑换券模板查询失败 couponId={}", couponId, e);
			}
		}
		return templates;
	}

	private PointsExchangeVO toVO(PointsExchange exchange, CouponTemplateInfoVO template, int balance,
			int exchangedCount) {
		PointsExchangeVO vo = new PointsExchangeVO();
		vo.setId(exchange.getId());
		vo.setTitle(exchange.getTitle());
		vo.setIconUrl(exchange.getIconUrl());
		vo.setPointsCost(exchange.getPointsCost());
		vo.setPerUserLimit(exchange.getPerUserLimit() == null ? 0 : exchange.getPerUserLimit());
		vo.setExchangedCount(exchangedCount);
		if (template != null) {
			vo.setCouponName(template.getCouponName());
			vo.setCouponType(template.getCouponType());
			vo.setCouponAmount(template.getAmount());
			vo.setCouponDiscount(template.getDiscount());
			vo.setCouponThreshold(template.getThreshold());
			vo.setCouponRemainNum(template.getRemainNum());
		}
		else {
			vo.setCouponName(exchange.getTitle());
		}
		String reason = resolveUnexchangeableReason(exchange, template, balance, exchangedCount);
		vo.setExchangeable(reason == null);
		vo.setUnexchangeableReason(reason);
		return vo;
	}

	private String resolveUnexchangeableReason(PointsExchange exchange, CouponTemplateInfoVO template, int balance,
			int exchangedCount) {
		if (template != null && template.getRemainNum() != null && template.getRemainNum() <= 0) {
			return "已被抢完";
		}
		if (exchange.getPerUserLimit() != null && exchange.getPerUserLimit() > 0
				&& exchangedCount >= exchange.getPerUserLimit()) {
			return "已达限兑次数";
		}
		if (balance < exchange.getPointsCost()) {
			return "积分不足";
		}
		return null;
	}

}
