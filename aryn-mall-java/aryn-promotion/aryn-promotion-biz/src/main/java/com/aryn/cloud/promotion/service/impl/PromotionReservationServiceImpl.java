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

	private final PromotionLockUsage promotionLockUsage;

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
		// preview 已剔除额度不足的活动；此处只处理竞态兜底与落锁
		PromotionCalculationVO calculation = promotionEngineService.preview(context);
		assertNewbieInFlightUnique(context, calculation);
		assertPerUserLimit(context, calculation);
		dropUnavailableGifts(context, calculation);
		for (PromotionCalculationVO.ActivityDetail detail : calculation.getDetails()) {
			if (!insertLock(context, detail)) {
				continue;
			}
			BigDecimal discount = detail.getDiscountAmount() != null ? detail.getDiscountAmount() : BigDecimal.ZERO;
			if (discount.compareTo(BigDecimal.ZERO) > 0 && promotionActivityMapper.consumeBudget(
					context.getTenantId(), detail.getActivityId(), discount) == 0) {
				// 试算后额度被并发订单抢完：整单回滚，让用户看到最新价格后再提交，避免静默涨价
				throw new ArynBusinessException("活动[" + detail.getActivityName() + "]优惠名额已用完，请刷新价格后重新提交");
			}
		}
		return calculation;
	}

	/** 落锁；并发命中唯一约束说明同单已被处理，跳过且不再重复占预算。 */
	private boolean insertLock(PromotionContextDTO context, PromotionCalculationVO.ActivityDetail detail) {
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
			return true;
		}
		catch (DuplicateKeyException ex) {
			log.info("订单[{}]活动[{}]并发锁定命中唯一约束，幂等跳过", context.getOrderId(), detail.getActivityId());
			return false;
		}
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

	/** 每人限享竞态兜底：preview 已按占用次数剔除，这里只防并发双单同时通过试算。 */
	private void assertPerUserLimit(PromotionContextDTO context, PromotionCalculationVO calculation) {
		if (!StringUtils.hasText(context.getUserId()) || calculation.getDetails().isEmpty()) {
			return;
		}
		for (PromotionCalculationVO.ActivityDetail detail : calculation.getDetails()) {
			PromotionActivity activity = promotionActivityMapper.selectById(detail.getActivityId());
			if (activity == null || activity.getPerUserLimit() == null || activity.getPerUserLimit() <= 0) {
				continue;
			}
			long used = promotionLockUsage.countUserLocks(context.getTenantId(), context.getUserId(),
					detail.getActivityId());
			if (used >= activity.getPerUserLimit()) {
				throw new ArynBusinessException("活动[" + detail.getActivityName() + "]每人限享次数已用完，请刷新价格后重新提交");
			}
		}
	}

	/**
	 * 赠品额度竞态兜底：并发把赠品抢完后本次降级为不赠，
	 * 赠品金额为 0，不影响订单应付，也不阻断付费商品成交。
	 */
	private void dropUnavailableGifts(PromotionContextDTO context, PromotionCalculationVO calculation) {
		for (PromotionCalculationVO.ActivityDetail detail : new ArrayList<>(calculation.getDetails())) {
			if (!PromotionActivity.TYPE_GIFT.equals(detail.getActivityType())) {
				continue;
			}
			PromotionActivity activity = promotionActivityMapper.selectById(detail.getActivityId());
			if (activity == null || activity.getGiftStock() == null || activity.getGiftStock() <= 0) {
				continue;
			}
			int current = calculation.getGifts().stream()
				.filter(gift -> detail.getActivityId().equals(gift.getActivityId()))
				.mapToInt(PromotionCalculationVO.GiftItem::getQuantity)
				.sum();
			int used = promotionLockUsage.sumGiftQuantity(context.getTenantId(), detail.getActivityId());
			if (used + current > activity.getGiftStock()) {
				calculation.getDetails().remove(detail);
				calculation.getGifts().removeIf(gift -> detail.getActivityId().equals(gift.getActivityId()));
				log.info("活动[{}]赠品额度已被并发占用，本次不赠 order={}", detail.getActivityId(), context.getOrderId());
			}
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
				BigDecimal discount = lock.getDiscountAmount() != null ? lock.getDiscountAmount() : BigDecimal.ZERO;
				if (discount.compareTo(BigDecimal.ZERO) > 0) {
					// 仅本次真正从 LOCKED/CONFIRMED 转 RELEASED 时归还一次，避免重复释放重复归还额度
					promotionActivityMapper.releaseBudget(tenantId, lock.getActivityId(), discount);
				}
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
