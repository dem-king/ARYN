package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.constant.MallEventConstants;
import com.aryn.cloud.promotion.api.dto.DistributionCouponExchangeDTO;
import com.aryn.cloud.promotion.api.entity.CouponInfo;
import com.aryn.cloud.promotion.api.entity.CouponUser;
import com.aryn.cloud.promotion.api.entity.DistributionCommissionFlow;
import com.aryn.cloud.promotion.api.entity.DistributionCouponExchange;
import com.aryn.cloud.promotion.api.entity.DistributionCouponPack;
import com.aryn.cloud.promotion.api.entity.DistributionCouponPackItem;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.api.enums.CommissionFlowTypeEnum;
import com.aryn.cloud.promotion.api.enums.CouponUserStatusEnum;
import com.aryn.cloud.promotion.api.enums.DistributionCouponExchangeStatusEnum;
import com.aryn.cloud.promotion.api.enums.DistributionCouponPackStatusEnum;
import com.aryn.cloud.promotion.api.vo.DistributionCouponCostVO;
import com.aryn.cloud.promotion.api.vo.DistributionCouponExchangeVO;
import com.aryn.cloud.promotion.mapper.CouponInfoMapper;
import com.aryn.cloud.promotion.mapper.CouponUserMapper;
import com.aryn.cloud.promotion.mapper.DistributionCommissionFlowMapper;
import com.aryn.cloud.promotion.mapper.DistributionCouponExchangeMapper;
import com.aryn.cloud.promotion.mapper.DistributionCouponPackItemMapper;
import com.aryn.cloud.promotion.mapper.DistributionCouponPackMapper;
import com.aryn.cloud.promotion.mapper.DistributionUserMapper;
import com.aryn.cloud.promotion.service.IDistributionCouponExchangeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分销佣金兑换优惠券
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DistributionCouponExchangeServiceImpl implements IDistributionCouponExchangeService {

	/** coupon_user 发放来源类型：佣金兑换券包 */
	private static final String SOURCE_TYPE_COUPON_EXCHANGE = "COUPON_EXCHANGE";

	/** distribution_coupon_exchange.coupon_user_ids varchar(500)，单个ID按 19 位+逗号估算，最多允许 20 张 */
	private static final int MAX_ISSUE_COUNT = 20;

	private final DistributionCouponPackMapper distributionCouponPackMapper;

	private final DistributionCouponPackItemMapper distributionCouponPackItemMapper;

	private final DistributionCouponExchangeMapper distributionCouponExchangeMapper;

	private final DistributionUserMapper distributionUserMapper;

	private final DistributionCommissionFlowMapper distributionCommissionFlowMapper;

	private final CouponInfoMapper couponInfoMapper;

	private final CouponUserMapper couponUserMapper;

	@Override
	public IPage<DistributionCouponExchangeVO> getAdminPage(Page<DistributionCouponExchange> page,
			DistributionCouponExchange query, LocalDateTime createTimeStart, LocalDateTime createTimeEnd) {
		DistributionCouponExchange safeQuery = query == null ? new DistributionCouponExchange() : query;
		IPage<DistributionCouponExchange> result = distributionCouponExchangeMapper.selectPage(page,
			Wrappers.<DistributionCouponExchange>lambdaQuery()
				.eq(StringUtils.hasText(safeQuery.getUserId()), DistributionCouponExchange::getUserId,
					safeQuery.getUserId())
				.eq(StringUtils.hasText(safeQuery.getPackId()), DistributionCouponExchange::getPackId,
					safeQuery.getPackId())
				.eq(StringUtils.hasText(safeQuery.getStatus()), DistributionCouponExchange::getStatus,
					safeQuery.getStatus())
				.ge(createTimeStart != null, DistributionCouponExchange::getCreateTime, createTimeStart)
				.le(createTimeEnd != null, DistributionCouponExchange::getCreateTime, createTimeEnd)
				.orderByDesc(DistributionCouponExchange::getExchangeTime)
				.orderByDesc(DistributionCouponExchange::getCreateTime));
		Page<DistributionCouponExchangeVO> voPage = new Page<>(result.getCurrent(), result.getSize(),
			result.getTotal());
		List<DistributionCouponExchangeVO> records = result.getRecords().stream().map(this::toVo).toList();
		fillCurrentNickname(records);
		voPage.setRecords(records);
		return voPage;
	}

	@Override
	public DistributionCouponCostVO getStatistics(DistributionCouponExchange query, LocalDateTime createTimeStart,
			LocalDateTime createTimeEnd) {
		DistributionCouponCostVO cost = distributionCouponExchangeMapper.selectCost(query, createTimeStart,
			createTimeEnd);
		if (cost == null) {
			cost = new DistributionCouponCostVO();
		}
		cost.setExchangeCount(cost.getExchangeCount() == null ? 0L : cost.getExchangeCount());
		cost.setCommissionAmount(cost.getCommissionAmount() == null ? BigDecimal.ZERO : cost.getCommissionAmount());
		cost.setFaceAmount(cost.getFaceAmount() == null ? BigDecimal.ZERO : cost.getFaceAmount());
		return cost;
	}

	@Override
	public IPage<DistributionCouponExchange> getUserPage(Page<DistributionCouponExchange> page, String userId) {
		return distributionCouponExchangeMapper.selectPage(page,
			Wrappers.<DistributionCouponExchange>lambdaQuery()
				.eq(DistributionCouponExchange::getUserId, userId)
				.orderByDesc(DistributionCouponExchange::getExchangeTime)
				.orderByDesc(DistributionCouponExchange::getCreateTime));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public Boolean exchange(String userId, DistributionCouponExchangeDTO dto) {
		if (!StringUtils.hasText(userId)) {
			throw new ArynBusinessException("请先登录后再兑换");
		}
		if (dto == null || !StringUtils.hasText(dto.getPackId())) {
			throw new ArynBusinessException("请选择要兑换的券包");
		}
		DistributionCouponPack pack = distributionCouponPackMapper.selectById(dto.getPackId());
		if (pack == null) {
			throw new ArynBusinessException("券包不存在或已下架");
		}
		if (!DistributionCouponPackStatusEnum.STATUS_0.getCode().equals(pack.getStatus())) {
			throw new ArynBusinessException("券包已下架");
		}
		List<DistributionCouponPackItem> items = distributionCouponPackItemMapper.selectList(
			Wrappers.<DistributionCouponPackItem>lambdaQuery()
				.eq(DistributionCouponPackItem::getPackId, pack.getId())
				.orderByAsc(DistributionCouponPackItem::getCreateTime));
		if (CollectionUtils.isEmpty(items)) {
			throw new ArynBusinessException("券包配置异常，暂时无法兑换");
		}

		// 1. 校验券模板有效性/剩余库存并计算发放张数
		Map<String, CouponInfo> templateMap = new LinkedHashMap<>();
		int issueCount = 0;
		for (DistributionCouponPackItem item : items) {
			int quantity = item.getQuantity() == null ? 1 : item.getQuantity();
			if (quantity <= 0) {
				throw new ArynBusinessException("券包配置异常，暂时无法兑换");
			}
			issueCount += quantity;
			CouponInfo template = templateMap.get(item.getCouponInfoId());
			if (template == null) {
				template = couponInfoMapper.selectCouponById(item.getCouponInfoId());
				if (template == null) {
					throw new ArynBusinessException("券包内优惠券已失效，暂时无法兑换");
				}
				templateMap.put(item.getCouponInfoId(), template);
			}
			if (template.getRemainNum() == null || template.getRemainNum() < quantity) {
				throw new ArynBusinessException("券包内优惠券库存不足，暂时无法兑换");
			}
		}
		if (issueCount > MAX_ISSUE_COUNT) {
			throw new ArynBusinessException("券包内优惠券数量超出限制，暂时无法兑换");
		}

		// 2. 行锁分销用户，校验可提现佣金（防并发重复扣减）
		DistributionUser user = distributionUserMapper.selectByUserIdForUpdate(userId);
		if (user == null) {
			throw new ArynBusinessException("分销用户不存在，请先注册分销员");
		}
		if (!MallEventConstants.DISTRIBUTION_USER_STATUS_ENABLE.equals(user.getStatus())) {
			throw new ArynBusinessException("分销用户已被禁用，无法兑换优惠券");
		}
		BigDecimal required = pack.getRequiredCommission() == null ? BigDecimal.ZERO : pack.getRequiredCommission();
		if (required.compareTo(BigDecimal.ZERO) < 0) {
			throw new ArynBusinessException("券包消耗佣金配置错误，暂时无法兑换");
		}
		BigDecimal available = user.getAvailableCommission() == null ? BigDecimal.ZERO : user.getAvailableCommission();
		if (available.compareTo(required) < 0) {
			throw new ArynBusinessException("可兑换佣金不足，当前可用佣金 " + available.setScale(2, RoundingMode.DOWN)
					+ " 元，需 " + required.setScale(2, RoundingMode.DOWN) + " 元");
		}

		// 3. 扣减券包有限库存（stock NULL=不限；原子更新防超卖）
		if (pack.getStock() != null) {
			if (pack.getStock() <= 0 || distributionCouponPackMapper.deductStock(pack.getId()) <= 0) {
				throw new ArynBusinessException("券包库存不足");
			}
		}

		// 4. 扣减可提现佣金（累计佣金 total_commission 不变），并写佣金支出流水
		BigDecimal balanceAfter = available.subtract(required);
		user.setAvailableCommission(balanceAfter);
		if (distributionUserMapper.updateById(user) <= 0) {
			throw new ArynBusinessException("佣金扣减失败，请重试");
		}
		String exchangeId = IdWorker.getIdStr();
		DistributionCommissionFlow flow = new DistributionCommissionFlow()
			.setUserId(userId)
			.setBizOrderId(exchangeId)
			.setFlowType(CommissionFlowTypeEnum.EXPENSE.getCode())
			.setAmount(required)
			.setBalanceAfter(balanceAfter)
			.setRemark("兑换优惠券-" + pack.getPackName());
		if (distributionCommissionFlowMapper.insert(flow) <= 0) {
			throw new ArynBusinessException("佣金流水保存失败，请重试");
		}

		// 5. 按明细逐张发券到 coupon_user：券发放失败抛异常回滚上面的佣金扣减
		String tenantId = resolveTenantId(user);
		LocalDateTime now = LocalDateTime.now();
		List<String> couponUserIds = new ArrayList<>();
		for (DistributionCouponPackItem item : items) {
			CouponInfo template = templateMap.get(item.getCouponInfoId());
			int quantity = item.getQuantity() == null ? 1 : item.getQuantity();
			for (int index = 0; index < quantity; index++) {
				CouponUser couponUser = new CouponUser()
					.setId(IdWorker.getIdStr())
					.setCouponId(template.getId())
					.setUserId(userId)
					.setStatus(CouponUserStatusEnum.STATUS_0.getCode())
					.setReceivedTime(now)
					.setValidatTime(resolveValidatTime(pack, template, now))
					.setTenantId(tenantId)
					.setSourceType(SOURCE_TYPE_COUPON_EXCHANGE)
					.setSourceId(exchangeId + "-" + couponUserIds.size());
				if (couponUserMapper.insertSourceIfAbsent(couponUser) <= 0) {
					throw new ArynBusinessException("优惠券发放失败，请重试");
				}
				if (couponInfoMapper.allocateOne(template.getId()) <= 0) {
					throw new ArynBusinessException("券包内优惠券库存不足，兑换失败");
				}
				couponUserIds.add(couponUser.getId());
			}
		}

		// 6. 写兑换记录（券发放成功才落库，同一事务提交）
		DistributionCouponExchange exchange = new DistributionCouponExchange()
			.setId(exchangeId)
			.setUserId(userId)
			.setNickname(user.getNickname())
			.setPackId(pack.getId())
			.setPackName(pack.getPackName())
			.setFaceAmount(pack.getFaceAmount())
			.setCommissionAmount(required)
			.setCouponUserIds(String.join(",", couponUserIds))
			.setStatus(DistributionCouponExchangeStatusEnum.STATUS_1.getCode())
			.setExchangeTime(now)
			.setRemark("佣金兑换优惠券")
			.setTenantId(tenantId);
		if (distributionCouponExchangeMapper.insert(exchange) <= 0) {
			throw new ArynBusinessException("兑换记录保存失败，请重试");
		}
		log.info("佣金兑换优惠券成功 exchangeId={}, userId={}, packId={}, commission={}, couponCount={}", exchangeId,
			userId, pack.getId(), required, couponUserIds.size());
		return Boolean.TRUE;
	}

	private LocalDateTime resolveValidatTime(DistributionCouponPack pack, CouponInfo template, LocalDateTime now) {
		if (pack.getValidDays() != null && pack.getValidDays() > 0) {
			return now.plusDays(pack.getValidDays());
		}
		return template.getReceiveEndedAt();
	}

	private String resolveTenantId(DistributionUser user) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		if (StringUtils.hasText(tenantId)) {
			return tenantId;
		}
		return user.getTenantId();
	}

	private DistributionCouponExchangeVO toVo(DistributionCouponExchange exchange) {
		if (exchange == null) {
			return null;
		}
		DistributionCouponExchangeVO vo = new DistributionCouponExchangeVO();
		BeanUtils.copyProperties(exchange, vo);
		vo.setUserNickname(exchange.getNickname());
		return vo;
	}

	/**
	 * 兑换记录里的昵称是兑换时快照；管理端再补一个当前昵称，便于按改名后的用户检索。
	 */
	private void fillCurrentNickname(List<DistributionCouponExchangeVO> records) {
		if (CollectionUtils.isEmpty(records)) {
			return;
		}
		List<String> userIds = records.stream().map(DistributionCouponExchangeVO::getUserId)
			.filter(StringUtils::hasText).distinct().toList();
		if (CollectionUtils.isEmpty(userIds)) {
			return;
		}
		Map<String, DistributionUser> userMap = distributionUserMapper.selectList(
			Wrappers.<DistributionUser>lambdaQuery().in(DistributionUser::getUserId, userIds))
			.stream()
			.collect(Collectors.toMap(DistributionUser::getUserId, Function.identity(), (left, right) -> left));
		records.forEach(record -> {
			DistributionUser current = userMap.get(record.getUserId());
			if (current != null && StringUtils.hasText(current.getNickname())) {
				record.setUserNickname(current.getNickname());
			}
		});
	}

}
