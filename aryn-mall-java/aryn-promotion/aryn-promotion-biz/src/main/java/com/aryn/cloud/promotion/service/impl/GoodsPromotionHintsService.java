package com.aryn.cloud.promotion.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 商详只读规则提示。订单门槛、优惠取优和库存资格仍由结算判断。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsPromotionHintsService {

	private static final Set<String> PUBLIC_SCOPES = Set.of("1", "2");
	private static final Set<String> SCENES = Set.of("1", "2", "3");
	/** 全场活动是订单级优惠，不在每个商品详情重复展示。 */
	private static final String FULL_SCOPE = "1";
	private static final int MAX_HINTS_PER_TYPE = 5;

	private final PromotionActivityMapper activityMapper;
	private final PromoCalendarService calendarService;

	@DubboReference
	private RemoteGoodsSkuService goodsSkuService;

	@DubboReference
	private RemoteVesselService vesselService;

	public record Hints(List<GiftHint> gifts, List<VolumeHint> volumes) {}
	public record GiftHint(String activityId, String name, BigDecimal thresholdAmount, List<Gift> gifts) {}
	public record Gift(String name, int quantity) {}
	public record VolumeHint(String activityId, String name, List<VolumeTier> tiers) {}
	public record VolumeTier(BigDecimal minAmount, Integer minQty, BigDecimal discountAmount) {}
	private record GiftRule(String skuId, int quantity) {}
	private record PendingGift(String activityId, String name, BigDecimal threshold, List<GiftRule> gifts) {}

	public Hints get(String tenantId, String userId, String spuId, String skuId, String scene, String vesselCallId) {
		if (!StringUtils.hasText(tenantId)) {
			return new Hints(List.of(), List.of());
		}
		List<String> ids = goodsSkuService.listBySpuIds(List.of(spuId)).stream().map(GoodsSku::getId).toList();
		if (ids.isEmpty()) {
			return new Hints(List.of(), List.of());
		}
		List<GoodsSku> active = goodsSkuService.getBySkuIds(ids);
		if (active.isEmpty() || (StringUtils.hasText(skuId) && active.stream().noneMatch(sku -> skuId.equals(sku.getId())))) {
			return new Hints(List.of(), List.of());
		}
		var context = context(tenantId, userId, scene, vesselCallId);
		// 多规格选择前只展示全场或场景规则，选中后再匹配指定 SKU。
		context.setSkuItems(active.stream()
			.filter(sku -> sku.getId().equals(skuId) || (!StringUtils.hasText(skuId) && active.size() == 1))
			.map(sku -> {
				var item = new PromotionContextDTO.SkuItem();
				item.setSkuId(sku.getId());
				return item;
			}).toList());
		LocalDateTime now = LocalDateTime.now();
		var activities = activityMapper.selectList(Wrappers.lambdaQuery(PromotionActivity.class)
			.eq(PromotionActivity::getTenantId, tenantId)
			.eq(PromotionActivity::getStatus, PromotionActivity.STATUS_PUBLISHED)
			.in(PromotionActivity::getActivityType, PromotionActivity.TYPE_GIFT, PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT)
			.le(PromotionActivity::getStartTime, now).ge(PromotionActivity::getEndTime, now)
			.orderByAsc(PromotionActivity::getEndTime, PromotionActivity::getId));
		var pendingGifts = new ArrayList<PendingGift>();
		var volumes = new ArrayList<VolumeHint>();
		for (var activity : calendarService.filterByCalendarWindow(tenantId, activities, now)) {
			// 全场活动（scope_type=1）对每个商品都成立，铺到商详会让所有商品显示同一张订单优惠卡；
			// 这类订单级优惠留给购物车/结算页，商详只展示与当前商品或当前场景真正相关的活动。
			if (FULL_SCOPE.equals(activity.getScopeType())) {
				continue;
			}
			if (activity.getScopeType() == null || (userId == null && !PUBLIC_SCOPES.contains(activity.getScopeType()))
					|| !PromotionScenarioEvaluator.matchesScope(activity, context, Set.of())) {
				continue;
			}
			try {
				JSONObject rules = JSONUtil.parseObj(activity.getRules());
				if (PromotionActivity.TYPE_GIFT.equals(activity.getActivityType()) && pendingGifts.size() < MAX_HINTS_PER_TYPE) {
					BigDecimal amount = rules.getBigDecimal("minAmount");
					List<GiftRule> gifts = giftRules(rules.getJSONArray("gifts"));
					if (amount != null && amount.signum() >= 0 && !gifts.isEmpty()) {
						pendingGifts.add(new PendingGift(activity.getId(), activity.getActivityName(), amount, gifts));
					}
				}
				else if (PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT.equals(activity.getActivityType())
						&& volumes.size() < MAX_HINTS_PER_TYPE && !Boolean.TRUE.equals(rules.getBool("firstOrderOnly"))) {
					List<VolumeTier> tiers = volumeTiers(rules.getJSONArray("tiers"), rules.getBigDecimal("maxDiscountAmount"));
					if (!tiers.isEmpty()) {
						volumes.add(new VolumeHint(activity.getId(), activity.getActivityName(), tiers));
					}
				}
			}
			catch (RuntimeException ex) {
				log.warn("活动 {} 规则无法用于商详提示", activity.getId());
			}
		}
		return new Hints(resolveGifts(pendingGifts), volumes);
	}

	private PromotionContextDTO context(String tenantId, String userId, String scene, String vesselCallId) {
		var context = new PromotionContextDTO();
		context.setTenantId(tenantId);
		context.setUserId(userId);
		if (userId != null && scene != null && SCENES.contains(scene)) {
			context.setPurchaseScene(scene);
		}
		if (userId != null && "2".equals(scene) && StringUtils.hasText(vesselCallId)) {
			try {
				var call = vesselService.getVesselCallContext(tenantId, vesselCallId);
				if (call != null && vesselService.isVesselMember(tenantId, call.getVesselId(), userId)) {
					context.setVesselId(call.getVesselId());
					context.setVesselCallId(call.getVesselCallId());
					context.setPortCode(call.getPortCode());
				}
			}
			catch (RuntimeException ex) {
				log.warn("靠港活动提示上下文读取失败");
			}
		}
		return context;
	}

	private List<GiftRule> giftRules(JSONArray rules) {
		var gifts = new ArrayList<GiftRule>();
		if (rules != null) {
			for (Object raw : rules) {
				if (raw instanceof JSONObject gift) {
					String skuId = gift.getStr("skuId");
					Integer quantity = gift.getInt("quantity");
					if (StringUtils.hasText(skuId) && quantity != null && quantity > 0) {
						gifts.add(new GiftRule(skuId, quantity));
					}
				}
			}
		}
		return gifts;
	}

	private List<GiftHint> resolveGifts(List<PendingGift> pending) {
		if (pending.isEmpty()) {
			return List.of();
		}
		List<String> ids = pending.stream().flatMap(hint -> hint.gifts().stream()).map(GiftRule::skuId).distinct().toList();
		Map<String, GoodsSku> available = new HashMap<>();
		for (GoodsSku sku : goodsSkuService.getBySkuIds(ids)) {
			if (sku.getGoodsSpu() != null && StringUtils.hasText(sku.getGoodsSpu().getName())) {
				available.put(sku.getId(), sku);
			}
		}
		var result = new ArrayList<GiftHint>();
		for (var hint : pending) {
			var gifts = new ArrayList<Gift>();
			for (var rule : hint.gifts()) {
				GoodsSku sku = available.get(rule.skuId());
				if (sku != null && sku.getStock() != null && sku.getStock() >= rule.quantity()) {
					gifts.add(new Gift(sku.getGoodsSpu().getName(), rule.quantity()));
				}
			}
			if (gifts.size() == hint.gifts().size()) {
				result.add(new GiftHint(hint.activityId(), hint.name(), hint.threshold(), gifts));
			}
		}
		return result;
	}

	private List<VolumeTier> volumeTiers(JSONArray rules, BigDecimal cap) {
		var tiers = new ArrayList<VolumeTier>();
		if (rules != null) {
			for (Object raw : rules) {
				if (raw instanceof JSONObject tier) {
					BigDecimal amount = tier.getBigDecimal("minAmount");
					Integer quantity = tier.getInt("minQty");
					BigDecimal discount = tier.getBigDecimal("discountAmount");
					if ((amount != null || quantity != null) && (amount == null || amount.signum() >= 0)
							&& (quantity == null || quantity > 0) && discount != null && discount.signum() > 0) {
						tiers.add(new VolumeTier(amount, quantity, cap != null && cap.signum() > 0 ? discount.min(cap) : discount));
					}
				}
			}
		}
		return tiers;
	}
}
