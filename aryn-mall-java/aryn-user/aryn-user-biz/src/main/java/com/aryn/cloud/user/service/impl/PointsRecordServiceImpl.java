package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.PointsGlobalConfig;
import com.aryn.cloud.user.api.entity.PointsRecord;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.enums.PointsSceneEnum;
import com.aryn.cloud.user.api.vo.PointsLiabilityDailyVO;
import com.aryn.cloud.user.api.vo.PointsLiabilityReportVO;
import com.aryn.cloud.user.api.vo.PointsRecordVO;
import com.aryn.cloud.user.mapper.PointsRecordMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IMemberLevelService;
import com.aryn.cloud.user.service.IPointsGlobalConfigService;
import com.aryn.cloud.user.service.IPointsGrantService;
import com.aryn.cloud.user.service.IPointsRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 积分记录
 *
 * @author 雨滴kian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointsRecordServiceImpl extends ServiceImpl<PointsRecordMapper, PointsRecord>
		implements IPointsRecordService {

	private final UserInfoMapper userInfoMapper;

	private final IMemberLevelService memberLevelService;

	private final IPointsGlobalConfigService pointsGlobalConfigService;

	// PointsGrantService 构造依赖本服务（账本写入），此处反向取汇率只能字段注入打破循环；
	// 极端情况下（独立装配无积分发放服务）按未配置汇率处理，金额列不折算
	@Autowired(required = false)
	private IPointsGrantService pointsGrantService;

	@Override
	public IPage<PointsRecordVO> getPage(Page page, String nickname, String changeType, String beginTime,
			String endTime) {
		return baseMapper.selectRecordPage(page, nickname, changeType, beginTime, endTime);
	}

	@Override
	public IPage<PointsRecordVO> getUserPage(Page page, String userId) {
		return baseMapper.selectUserRecordPage(page, userId);
	}

	/**
	 * 积分负债报表（读侧聚合，零 DDL）：区间日报按日拆解，金额按抵现汇率面值折算；
	 * 期末负债 = 全体用户未消耗积分合计。汇率未配置（返回 0）时金额列全部为 null。
	 */
	@Override
	public PointsLiabilityReportVO getLiabilityReport(String beginDate, String endDate) {
		int rate = pointsGrantService == null ? 0 : pointsGrantService.getPointsPerYuan();
		List<PointsLiabilityDailyVO> days = baseMapper.selectLiabilityDaily(beginDate, endDate);
		long rangeGrant = 0;
		long rangeConsume = 0;
		long rangeExpire = 0;
		for (PointsLiabilityDailyVO day : days) {
			if (rate > 0) {
				day.setGrantAmount(toAmount(day.getGrantPoints(), rate));
				day.setDeductAmount(toAmount(day.getDeductPoints(), rate));
				day.setExchangeAmount(toAmount(day.getExchangePoints(), rate));
				day.setExpireAmount(toAmount(day.getExpirePoints(), rate));
			}
			rangeGrant += nvl(day.getGrantPoints());
			rangeConsume += nvl(day.getDeductPoints()) + nvl(day.getExchangePoints())
					+ nvl(day.getAdminDeductPoints());
			rangeExpire += nvl(day.getExpirePoints());
		}
		PointsLiabilityReportVO report = new PointsLiabilityReportVO();
		report.setPointsPerYuan(rate);
		report.setDays(days);
		report.setBalancePoints(userInfoMapper.sumPoints());
		report.setBalanceAmount(toAmount(report.getBalancePoints() == null ? 0 : report.getBalancePoints().intValue(),
				rate));
		PointsLiabilityReportVO.Summary summary = baseMapper.selectLiabilitySummary();
		if (summary != null) {
			report.setTotalGrantPoints(summary.getTotalGrantPoints());
			report.setTotalDeductPoints(summary.getTotalDeductPoints());
			report.setTotalExchangePoints(summary.getTotalExchangePoints());
			report.setTotalExpirePoints(summary.getTotalExpirePoints());
			report.setTotalRefundReturnPoints(summary.getTotalRefundReturnPoints());
		}
		report.setRangeGrantPoints(rangeGrant);
		report.setRangeConsumePoints(rangeConsume);
		report.setRangeExpirePoints(rangeExpire);
		return report;
	}

	private java.math.BigDecimal toAmount(Integer points, int rate) {
		if (rate <= 0 || points == null) {
			return null;
		}
		return java.math.BigDecimal.valueOf(points)
			.divide(java.math.BigDecimal.valueOf(rate), 2, java.math.RoundingMode.DOWN);
	}

	private long nvl(Integer value) {
		return value == null ? 0L : value;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void recordPointsChange(String userId, String changeType, Integer changePoint, String triggerScene,
			String remark) {
		recordPointsChange(userId, changeType, changePoint, triggerScene, remark, null);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void recordPointsChange(String userId, String changeType, Integer changePoint, String triggerScene,
			String remark, String bizNo) {
		if (!"1".equals(changeType) && !"2".equals(changeType)) {
			throw new ArynBusinessException("积分变动类型不合法");
		}
		if (changePoint == null || changePoint <= 0) {
			throw new ArynBusinessException("积分变动值必须大于0");
		}
		if (StringUtils.hasText(bizNo)) {
			long exists = this.count(Wrappers.<PointsRecord>lambdaQuery().eq(PointsRecord::getBizNo, bizNo));
			if (exists > 0) {
				// 同一业务单号已入账：MQ 重投或超时/取消并发时直接返回，避免重复加减
				return;
			}
		}

		UserInfo userInfo = userInfoMapper.selectById(userId);
		if (userInfo == null) {
			throw new ArynBusinessException("用户不存在");
		}
		Integer currentPoint = userInfoMapper.selectPointForUpdate(userId);
		if (currentPoint == null) {
			throw new ArynBusinessException("用户不存在");
		}

		// 退款/取消返还不是「新获得」：只恢复可用积分，不增加 total_point（累计获得），
		// 否则反复「下单-取消」即可刷高累计积分与积分线等级
		boolean refundScene = PointsSceneEnum.ORDER_REFUND.getCode().equals(triggerScene);
		int affectedRows;
		if ("1".equals(changeType)) {
			affectedRows = refundScene ? userInfoMapper.refundPoints(userId, changePoint)
					: userInfoMapper.acquirePoints(userId, changePoint);
		}
		else {
			affectedRows = userInfoMapper.consumePoints(userId, changePoint);
			if (affectedRows == 0) {
				throw new ArynBusinessException("积分余额不足");
			}
		}
		if (affectedRows == 0) {
			throw new ArynBusinessException("积分变动失败");
		}

		int balanceAfter = "1".equals(changeType) ? currentPoint + changePoint : currentPoint - changePoint;
		PointsRecord record = new PointsRecord();
		record.setUserId(userId);
		record.setChangeType(changeType);
		record.setChangePoint(changePoint);
		record.setBalanceAfter(balanceAfter);
		record.setTriggerScene(triggerScene);
		record.setRemark(remark);
		record.setBizNo(bizNo);
		if ("1".equals(changeType)) {
			// 发放/返还类流水按批次记账：expire_time + remain_point 供 FIFO 消耗与到期清零
			record.setExpireTime(resolveExpireTime());
			record.setRemainPoint(changePoint);
		}
		else {
			allocateConsumeRemain(userId, changePoint);
		}
		this.save(record);

		if ("1".equals(changeType) && !refundScene) {
			memberLevelService.recalculateLevel(userId);
		}
	}

	/**
	 * 消耗时按「最早到期优先、永不过期批次垫底」扣减各发放批次的剩余量（FIFO）。
	 *
	 * <p>用户行锁（selectPointForUpdate）已串行化同用户积分变动，批次扣减按
	 * 条件 UPDATE 原子递减即可；批次剩余量不足时不阻断主流程（余额已够），
	 * 只记告警——存量未回填/历史账实漂移时消耗侧以余额为准，记账尽力而为。
	 */
	private void allocateConsumeRemain(String userId, int changePoint) {
		int remaining = changePoint;
		List<PointsRecord> batches = this.list(Wrappers.<PointsRecord>lambdaQuery()
			.select(PointsRecord::getId, PointsRecord::getRemainPoint)
			.eq(PointsRecord::getUserId, userId)
			.gt(PointsRecord::getRemainPoint, 0)
			.and(wrapper -> wrapper.isNull(PointsRecord::getExpireTime)
				.or()
				.gt(PointsRecord::getExpireTime, LocalDateTime.now()))
			// MySQL ASC 排序 NULL 在前，须显式把「永不过期」批次排到最后
			.last("ORDER BY expire_time IS NULL, expire_time ASC, create_time ASC, id ASC"));
		for (PointsRecord batch : batches) {
			if (remaining <= 0) {
				return;
			}
			int take = Math.min(remaining, batch.getRemainPoint() == null ? 0 : batch.getRemainPoint());
			if (take <= 0) {
				continue;
			}
			boolean decreased = this.update(Wrappers.<PointsRecord>lambdaUpdate()
				.eq(PointsRecord::getId, batch.getId())
				.ge(PointsRecord::getRemainPoint, take)
				.setSql("remain_point = remain_point - " + take));
			if (decreased) {
				remaining -= take;
			}
		}
		if (remaining > 0) {
			log.warn("积分批次剩余量不足以覆盖消耗 userId={} 缺口={}（存量未回填或账实漂移，按余额口径放行）", userId,
					remaining);
		}
	}

	/**
	 * 新发放批次的有效期：配置 0 或缺省异常时视为永不过期（expire_time 置 NULL）。
	 */
	private LocalDateTime resolveExpireTime() {
		try {
			PointsGlobalConfig config = pointsGlobalConfigService.getEffectiveConfig();
			Integer days = config == null ? null : config.getValidityDays();
			if (days == null || days <= 0) {
				return null;
			}
			return LocalDateTime.now().plusDays(days);
		}
		catch (Exception e) {
			log.warn("读取积分有效期配置失败，本批次按永不过期记账", e);
			return null;
		}
	}

}
