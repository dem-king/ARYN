package com.aryn.cloud.promotion.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.entity.PromotionLock;
import com.aryn.cloud.promotion.mapper.PromotionLockMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 营销锁使用量查询。
 *
 * <p>试算过滤（活动是否还有额度）与实际锁定（并发兜底）共用同一口径：
 * 只统计 LOCKED/CONFIRMED，已释放（取消/超时）不占额度。
 *
 * @author aryn
 * @since 2026/10/22
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PromotionLockUsage {

	private final PromotionLockMapper promotionLockMapper;

	/** 用户在某活动下已占用的次数。 */
	public long countUserLocks(String tenantId, String userId, String activityId) {
		Long count = promotionLockMapper.selectCount(Wrappers.lambdaQuery(PromotionLock.class)
			.eq(PromotionLock::getTenantId, tenantId)
			.eq(PromotionLock::getUserId, userId)
			.eq(PromotionLock::getActivityId, activityId)
			.in(PromotionLock::getStatus, List.of(PromotionLock.STATUS_LOCKED, PromotionLock.STATUS_CONFIRMED)));
		return count == null ? 0 : count;
	}

	/** 活动已占用的赠品数量（从锁定快照恢复）。 */
	public int sumGiftQuantity(String tenantId, String activityId) {
		return promotionLockMapper.selectList(Wrappers.lambdaQuery(PromotionLock.class)
				.eq(PromotionLock::getTenantId, tenantId)
				.eq(PromotionLock::getActivityId, activityId)
				.eq(PromotionLock::getActivityType, PromotionActivity.TYPE_GIFT)
				.in(PromotionLock::getStatus, List.of(PromotionLock.STATUS_LOCKED, PromotionLock.STATUS_CONFIRMED)))
			.stream()
			.mapToInt(this::giftQuantity)
			.sum();
	}

	private int giftQuantity(PromotionLock lock) {
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

}
