package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.entity.PromotionLock;
import com.aryn.cloud.promotion.api.vo.PromotionCalculationVO;
import com.aryn.cloud.promotion.mapper.PromotionLockMapper;
import com.aryn.cloud.promotion.service.PromotionEngineService;
import com.aryn.cloud.promotion.service.PromotionReservationService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 营销锁定服务实现。
 *
 * <p>reserve 以租户+订单+活动唯一键幂等：并发重复下单只保留一条锁定，
 * 重复调用返回首次锁定的计算结果；confirm/release 仅做状态推进（幂等）。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionReservationServiceImpl implements PromotionReservationService {

	private final PromotionEngineService promotionEngineService;

	private final PromotionLockMapper promotionLockMapper;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public PromotionCalculationVO reserve(PromotionContextDTO context) {
		if (context == null || !StringUtils.hasText(context.getTenantId())
				|| !StringUtils.hasText(context.getOrderId())) {
			throw new ArynBusinessException("营销锁定缺少租户或订单信息");
		}
		PromotionLock existing = findLock(context.getTenantId(), context.getOrderId(), null);
		if (existing != null) {
			if (PromotionLock.STATUS_RELEASED.equals(existing.getStatus())) {
				// 原订单已释放（取消后重下单 requestId 不同不会命中；同单重放按已释放拒绝）
				throw new ArynBusinessException("订单营销优惠已释放，无法重复锁定");
			}
			return rebuildFromLock(existing);
		}
		PromotionCalculationVO calculation = promotionEngineService.preview(context);
		for (PromotionCalculationVO.ActivityDetail detail : calculation.getDetails()) {
			PromotionLock lock = new PromotionLock();
			lock.setOrderId(context.getOrderId());
			lock.setOrderNo(context.getOrderNo());
			lock.setActivityId(detail.getActivityId());
			lock.setActivityType(detail.getActivityType());
			lock.setActivityName(detail.getActivityName());
			lock.setDiscountAmount(detail.getDiscountAmount() != null ? detail.getDiscountAmount() : BigDecimal.ZERO);
			lock.setRuleSnapshot(detail.getRuleSnapshot());
			lock.setStatus(PromotionLock.STATUS_LOCKED);
			lock.setTenantId(context.getTenantId());
			lock.setCreateTime(LocalDateTime.now());
			lock.setDelFlag("0");
			try {
				promotionLockMapper.insert(lock);
			}
			catch (DuplicateKeyException ex) {
				log.info("订单[{}]活动[{}]并发锁定命中唯一约束，幂等跳过", context.getOrderId(), detail.getActivityId());
			}
		}
		return calculation;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void confirm(String tenantId, String orderId) {
		List<PromotionLock> locks = findLocks(tenantId, orderId);
		for (PromotionLock lock : locks) {
			if (PromotionLock.STATUS_LOCKED.equals(lock.getStatus())) {
				lock.setStatus(PromotionLock.STATUS_CONFIRMED);
				lock.setUpdateTime(LocalDateTime.now());
				promotionLockMapper.updateById(lock);
			}
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void release(String tenantId, String orderId, String reason) {
		List<PromotionLock> locks = findLocks(tenantId, orderId);
		for (PromotionLock lock : locks) {
			if (!PromotionLock.STATUS_RELEASED.equals(lock.getStatus())) {
				lock.setStatus(PromotionLock.STATUS_RELEASED);
				lock.setReleaseReason(reason);
				lock.setUpdateTime(LocalDateTime.now());
				promotionLockMapper.updateById(lock);
			}
		}
	}

	private PromotionLock findLock(String tenantId, String orderId, String activityId) {
		return promotionLockMapper.selectOne(Wrappers.lambdaQuery(PromotionLock.class)
				.eq(PromotionLock::getTenantId, tenantId)
				.eq(PromotionLock::getOrderId, orderId)
				.eq(activityId != null, PromotionLock::getActivityId, activityId)
				.last("LIMIT 1"));
	}

	private List<PromotionLock> findLocks(String tenantId, String orderId) {
		return promotionLockMapper.selectList(Wrappers.lambdaQuery(PromotionLock.class)
				.eq(PromotionLock::getTenantId, tenantId)
				.eq(PromotionLock::getOrderId, orderId));
	}

	/**
	 * 从已存在的锁定记录重建计算结果（幂等返回）。
	 */
	private PromotionCalculationVO rebuildFromLock(PromotionLock lock) {
		PromotionCalculationVO calculation = new PromotionCalculationVO();
		calculation.setTotalDiscount(lock.getDiscountAmount() != null ? lock.getDiscountAmount() : BigDecimal.ZERO);
		calculation.setWholeDiscount(
				Objects.equals("7", lock.getActivityType()) ? lock.getDiscountAmount() : BigDecimal.ZERO);
		calculation.setLadderOverrides(new ArrayList<>());
		PromotionCalculationVO.ActivityDetail detail = new PromotionCalculationVO.ActivityDetail();
		detail.setActivityId(lock.getActivityId());
		detail.setActivityType(lock.getActivityType());
		detail.setActivityName(lock.getActivityName());
		detail.setDiscountAmount(lock.getDiscountAmount());
		detail.setRuleSnapshot(lock.getRuleSnapshot());
		calculation.setDetails(new ArrayList<>(List.of(detail)));
		return calculation;
	}

}
