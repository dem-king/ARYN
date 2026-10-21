package com.aryn.cloud.promotion.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.entity.PromotionLock;
import com.aryn.cloud.promotion.api.vo.PromotionCalculationVO;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
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

	private final PromotionActivityMapper promotionActivityMapper;

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
			return rebuildFromLocks(findLocks(context.getTenantId(), context.getOrderId()));
		}
		PromotionCalculationVO calculation = promotionEngineService.preview(context);
		assertNewbieInFlightUnique(context, calculation);
		assertFuseLimits(context, calculation);
		for (PromotionCalculationVO.ActivityDetail detail : calculation.getDetails()) {
			PromotionLock lock = new PromotionLock();
			lock.setOrderId(context.getOrderId());
			lock.setOrderNo(context.getOrderNo());
			lock.setActivityId(detail.getActivityId());
			lock.setActivityType(detail.getActivityType());
			lock.setUserId(context.getUserId());
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

	/**
	 * 新人价「在途唯一」：本单命中新人价、且同一用户已有 LOCKED 的新人价锁时拒绝。
	 *
	 * <p>防「同时下两单都锁新人价、先后支付两单都享价」。对货到付款尤其关键：
	 * COD 单的 pay_status 要等确认收款才翻 1，身份判定在此之前仍视其为新人，
	 * 长窗口只能靠在途锁兜住。只在本单命中的情况下检查——不含新人价的普通订单
	 * 不受此人已有待支付新人价单的影响。
	 * 只查 LOCKED：已确认（已支付，身份判定自然失效）与已释放（可重新下单）均不拦截。
	 * 查询失败按放行处理，不阻断下单主流程。
	 */
	private void assertNewbieInFlightUnique(PromotionContextDTO context, PromotionCalculationVO calculation) {
		if (!StringUtils.hasText(context.getUserId()) || calculation == null) {
			return;
		}
		boolean newbieHit = calculation.getDetails().stream()
			.anyMatch(detail -> PromotionActivity.TYPE_NEWBIE_PRICE.equals(detail.getActivityType()));
		if (!newbieHit) {
			return;
		}
		try {
			Long locked = promotionLockMapper.selectCount(Wrappers.lambdaQuery(PromotionLock.class)
				.eq(PromotionLock::getTenantId, context.getTenantId())
				.eq(PromotionLock::getUserId, context.getUserId())
				.eq(PromotionLock::getActivityType, PromotionActivity.TYPE_NEWBIE_PRICE)
				.eq(PromotionLock::getStatus, PromotionLock.STATUS_LOCKED));
			if (locked != null && locked > 0) {
				throw new ArynBusinessException("已有新人专享订单待完成，暂不能重复使用新人专享价");
			}
		}
		catch (ArynBusinessException ex) {
			throw ex;
		}
		catch (Exception ex) {
			log.warn("新人价在途唯一查询失败，按放行处理 tenant={} user={}", context.getTenantId(),
					context.getUserId(), ex);
		}
	}

	/**
	 * 熔断：活动预算、每人限享次数、买赠赠品总量超限时整单拦截。
	 * 聚合口径为存量锁（LOCKED/CONFIRMED）+ 本次命中；赠品数量从锁 ruleSnapshot 恢复。
	 */
	private void assertFuseLimits(PromotionContextDTO context, PromotionCalculationVO calculation) {
		if (calculation.getDetails() == null || calculation.getDetails().isEmpty()) {
			return;
		}
		for (PromotionCalculationVO.ActivityDetail detail : calculation.getDetails()) {
			PromotionActivity activity = promotionActivityMapper.selectById(detail.getActivityId());
			if (activity == null) {
				continue;
			}
			BigDecimal current = detail.getDiscountAmount() != null ? detail.getDiscountAmount() : BigDecimal.ZERO;
			if (activity.getTotalBudget() != null && activity.getTotalBudget().compareTo(BigDecimal.ZERO) > 0) {
				BigDecimal used = sumLockedDiscount(context.getTenantId(), detail.getActivityId());
				if (used.add(current).compareTo(activity.getTotalBudget()) > 0) {
					throw new ArynBusinessException("活动[" + activity.getActivityName() + "]预算不足，本次优惠不可用");
				}
			}
			if (activity.getPerUserLimit() != null && activity.getPerUserLimit() > 0
					&& StringUtils.hasText(context.getUserId())) {
				Long locked = promotionLockMapper.selectCount(Wrappers.lambdaQuery(PromotionLock.class)
					.eq(PromotionLock::getTenantId, context.getTenantId())
					.eq(PromotionLock::getUserId, context.getUserId())
					.eq(PromotionLock::getActivityId, detail.getActivityId())
					.in(PromotionLock::getStatus,
							List.of(PromotionLock.STATUS_LOCKED, PromotionLock.STATUS_CONFIRMED)));
				if (locked != null && locked >= activity.getPerUserLimit()) {
					throw new ArynBusinessException("活动[" + activity.getActivityName() + "]每人限享次数已用完");
				}
			}
			if (PromotionActivity.TYPE_GIFT.equals(detail.getActivityType())
					&& activity.getGiftStock() != null && activity.getGiftStock() > 0) {
				int currentQty = calculation.getGifts().stream()
					.filter(gift -> detail.getActivityId().equals(gift.getActivityId()))
					.mapToInt(PromotionCalculationVO.GiftItem::getQuantity)
					.sum();
				int usedQty = sumLockedGiftQuantity(context.getTenantId(), detail.getActivityId());
				if (usedQty + currentQty > activity.getGiftStock()) {
					throw new ArynBusinessException("活动[" + activity.getActivityName() + "]赠品总量不足，本次赠品不可用");
				}
			}
		}
	}

	private BigDecimal sumLockedDiscount(String tenantId, String activityId) {
		List<PromotionLock> locks = promotionLockMapper.selectList(Wrappers.lambdaQuery(PromotionLock.class)
			.eq(PromotionLock::getTenantId, tenantId)
			.eq(PromotionLock::getActivityId, activityId)
			.in(PromotionLock::getStatus,
					List.of(PromotionLock.STATUS_LOCKED, PromotionLock.STATUS_CONFIRMED)));
		return locks.stream()
			.map(lock -> lock.getDiscountAmount() != null ? lock.getDiscountAmount() : BigDecimal.ZERO)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private int sumLockedGiftQuantity(String tenantId, String activityId) {
		List<PromotionLock> locks = promotionLockMapper.selectList(Wrappers.lambdaQuery(PromotionLock.class)
			.eq(PromotionLock::getTenantId, tenantId)
			.eq(PromotionLock::getActivityId, activityId)
			.eq(PromotionLock::getActivityType, PromotionActivity.TYPE_GIFT)
			.in(PromotionLock::getStatus,
					List.of(PromotionLock.STATUS_LOCKED, PromotionLock.STATUS_CONFIRMED)));
		return locks.stream().mapToInt(this::lockedGiftQuantity).sum();
	}

	private int lockedGiftQuantity(PromotionLock lock) {
		try {
			JSONArray giftArray = JSONUtil.parseObj(lock.getRuleSnapshot()).getJSONArray("gifts");
			if (giftArray == null) {
				return 0;
			}
			return giftArray.stream()
				.map(element -> (JSONObject) element)
				.mapToInt(gift -> gift.getInt("quantity") == null ? 0 : gift.getInt("quantity"))
				.sum();
		}
		catch (Exception ex) {
			log.warn("锁定[订单{}]赠品快照解析失败，按 0 计", lock.getOrderId(), ex);
			return 0;
		}
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
	 * 从订单全部锁定记录重建计算结果（幂等返回）：明细与金额逐锁聚合，
	 * 买赠赠品从锁的 ruleSnapshot 恢复。历史实现只取单条锁（LIMIT 1），
	 * 重放时赠品与其余活动明细丢失，导致同单重放追加不了赠品。
	 */
	private PromotionCalculationVO rebuildFromLocks(List<PromotionLock> locks) {
		PromotionCalculationVO calculation = new PromotionCalculationVO();
		calculation.setLadderOverrides(new ArrayList<>());
		calculation.setGifts(new ArrayList<>());
		calculation.setDetails(new ArrayList<>());
		BigDecimal total = BigDecimal.ZERO;
		BigDecimal whole = BigDecimal.ZERO;
		for (PromotionLock lock : locks) {
			BigDecimal discount = lock.getDiscountAmount() != null ? lock.getDiscountAmount() : BigDecimal.ZERO;
			total = total.add(discount);
			if (PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT.equals(lock.getActivityType())) {
				whole = whole.add(discount);
			}
			PromotionCalculationVO.ActivityDetail detail = new PromotionCalculationVO.ActivityDetail();
			detail.setActivityId(lock.getActivityId());
			detail.setActivityType(lock.getActivityType());
			detail.setActivityName(lock.getActivityName());
			detail.setDiscountAmount(lock.getDiscountAmount());
			detail.setRuleSnapshot(lock.getRuleSnapshot());
			calculation.getDetails().add(detail);
			if (PromotionActivity.TYPE_GIFT.equals(lock.getActivityType())) {
				calculation.getGifts().addAll(parseLockedGifts(lock));
			}
		}
		calculation.setTotalDiscount(total);
		calculation.setWholeDiscount(whole);
		return calculation;
	}

	private List<PromotionCalculationVO.GiftItem> parseLockedGifts(PromotionLock lock) {
		List<PromotionCalculationVO.GiftItem> gifts = new ArrayList<>();
		try {
			JSONArray giftArray = JSONUtil.parseObj(lock.getRuleSnapshot()).getJSONArray("gifts");
			if (giftArray == null) {
				return gifts;
			}
			for (Object element : giftArray) {
				JSONObject gift = (JSONObject) element;
				String skuId = gift.getStr("skuId");
				Integer quantity = gift.getInt("quantity");
				if (!StringUtils.hasText(skuId) || quantity == null || quantity < 1) {
					continue;
				}
				PromotionCalculationVO.GiftItem item = new PromotionCalculationVO.GiftItem();
				item.setSkuId(skuId);
				item.setQuantity(quantity);
				item.setActivityId(lock.getActivityId());
				gifts.add(item);
			}
		}
		catch (Exception ex) {
			log.warn("锁定[订单{}活动{}]买赠快照解析失败，按无赠品返回", lock.getOrderId(), lock.getActivityId(), ex);
		}
		return gifts;
	}

}
