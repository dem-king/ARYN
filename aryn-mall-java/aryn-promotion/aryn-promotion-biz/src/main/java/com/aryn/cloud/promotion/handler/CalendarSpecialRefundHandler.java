package com.aryn.cloud.promotion.handler;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.entity.PromotionLock;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.mapper.PromotionLockMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 档期特供退款回补限量（2026-11-15 审查修复新增）。
 *
 * <p>退款事件按订单明细整行触发：退回该 SKU 在特供锁中占用的限量件数，
 * 活动 {@code consumed_quota} 原子归还，让后续买家能买到退回的名额。
 * 幂等由锁上 {@code refunded_quota_units} 原子累计闸门保证（MQ 重复投递不重复回补）；
 * 取消/超时整单释放走 {@code PromotionReservationServiceImpl#release}，与本链路互不重叠
 * （本处理器只认 CONFIRMED 锁）。
 *
 * @author aryn
 * @since 2026/11/15
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CalendarSpecialRefundHandler implements PromotionRefundEventHandler {

	private final PromotionLockMapper promotionLockMapper;

	private final PromotionActivityMapper promotionActivityMapper;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void handle(OrderRefundSuccessEvent event) {
		if (event == null || !StringUtils.hasText(event.getOrderId()) || event.getOrderItem() == null
				|| !StringUtils.hasText(event.getOrderItem().getSkuId())) {
			return;
		}
		String skuId = event.getOrderItem().getSkuId();
		List<PromotionLock> locks = promotionLockMapper.selectList(Wrappers.lambdaQuery(PromotionLock.class)
			.eq(PromotionLock::getTenantId, event.getTenantId())
			.eq(PromotionLock::getOrderId, event.getOrderId())
			.eq(PromotionLock::getActivityType, PromotionActivity.TYPE_CALENDAR_SPECIAL)
			.eq(PromotionLock::getStatus, PromotionLock.STATUS_CONFIRMED));
		for (PromotionLock lock : locks) {
			int quotaUnits = quotaUnits(lock);
			if (quotaUnits <= 0 || !snapshotContainsSku(lock, skuId)) {
				continue;
			}
			Integer buyQuantity = event.getOrderItem().getBuyQuantity();
			int units = buyQuantity == null || buyQuantity <= 0 ? quotaUnits : Math.min(buyQuantity, quotaUnits);
			// 幂等闸门先行：重复投递/超额回补返回 0，直接跳过，不动活动额度
			if (promotionActivityMapper.accumulateRefundedQuota(event.getTenantId(), lock.getId(), units,
					quotaUnits) == 0) {
				log.info("档期特供退款回补重复/超额，跳过 lock={} order={} units={}", lock.getId(), event.getOrderId(), units);
				continue;
			}
			promotionActivityMapper.releaseQuota(event.getTenantId(), lock.getActivityId(), units);
			log.info("档期特供退款回补限量 lock={} activity={} order={} sku={} units={}", lock.getId(),
					lock.getActivityId(), event.getOrderId(), skuId, units);
		}
	}

	private int quotaUnits(PromotionLock lock) {
		try {
			Integer units = JSONUtil.parseObj(lock.getRuleSnapshot()).getInt("quotaUnits");
			return units == null || units <= 0 ? 0 : units;
		}
		catch (Exception ex) {
			log.warn("特供锁[{}]限量快照解析失败，跳过回补", lock.getId(), ex);
			return 0;
		}
	}

	/** 锁快照 rules.skus 是否包含退款 SKU（特供锁按活动落快照，同活动多 SKU 时只有命中行才占额度）。 */
	private boolean snapshotContainsSku(PromotionLock lock, String skuId) {
		try {
			JSONArray skus = JSONUtil.parseObj(lock.getRuleSnapshot()).getJSONArray("skus");
			if (skus == null) {
				return false;
			}
			for (Object element : skus) {
				JSONObject sku = (JSONObject) element;
				if (skuId.equals(sku.getStr("skuId"))) {
					return true;
				}
			}
			return false;
		}
		catch (Exception ex) {
			log.warn("特供锁[{}]规则快照解析失败，跳过回补", lock.getId(), ex);
			return false;
		}
	}

}