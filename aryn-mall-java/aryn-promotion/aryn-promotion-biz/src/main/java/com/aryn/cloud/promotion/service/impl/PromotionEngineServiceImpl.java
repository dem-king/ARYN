package com.aryn.cloud.promotion.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.vo.PromotionCalculationVO;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.service.PromotionEngineService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 统一营销计算引擎实现。
 *
 * <p>活动匹配规则（scope_type）：1 全场、2 指定 SKU、4 指定购买场景、
 * 5 指定船舶、6 指定靠港计划、7 指定港口；purchase_scene 非空时额外过滤。
 * 同类型多活动命中取优惠最大者；解析失败的活动跳过并告警，不阻断结算。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromotionEngineServiceImpl implements PromotionEngineService {

	private final PromotionActivityMapper promotionActivityMapper;

	@Override
	public PromotionCalculationVO preview(PromotionContextDTO context) {
		PromotionCalculationVO result = new PromotionCalculationVO();
		result.setTotalDiscount(BigDecimal.ZERO);
		result.setWholeDiscount(BigDecimal.ZERO);
		result.setLadderOverrides(new ArrayList<>());
		result.setDetails(new ArrayList<>());
		result.setGifts(new ArrayList<>());
		if (context == null || context.getTenantId() == null || context.getSkuItems() == null
				|| context.getSkuItems().isEmpty()) {
			return result;
		}
		LocalDateTime now = context.getActivityTime() != null ? context.getActivityTime() : LocalDateTime.now();
		List<PromotionActivity> activities = promotionActivityMapper.selectList(
				Wrappers.lambdaQuery(PromotionActivity.class)
						.eq(PromotionActivity::getTenantId, context.getTenantId())
						.eq(PromotionActivity::getStatus, PromotionActivity.STATUS_PUBLISHED)
						.le(PromotionActivity::getStartTime, now)
						.ge(PromotionActivity::getEndTime, now)
						.in(PromotionActivity::getActivityType,
								List.of(PromotionActivity.TYPE_LADDER_PRICE, PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT,
										PromotionActivity.TYPE_GIFT)));

		List<PromotionActivity> ladderHits = new ArrayList<>();
		List<PromotionActivity> wholeHits = new ArrayList<>();
		List<PromotionActivity> giftHits = new ArrayList<>();
		for (PromotionActivity activity : activities) {
			if (!matchesScope(activity, context)) {
				continue;
			}
			if (PromotionActivity.TYPE_LADDER_PRICE.equals(activity.getActivityType())) {
				ladderHits.add(activity);
			}
			else if (PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT.equals(activity.getActivityType())) {
				wholeHits.add(activity);
			}
			else if (PromotionActivity.TYPE_GIFT.equals(activity.getActivityType())) {
				giftHits.add(activity);
			}
		}

		applyBestLadderPrice(context, ladderHits, result);
		applyBestWholeDiscount(context, wholeHits, result);
		applyBestGift(context, giftHits, result);
		return result;
	}

	/**
	 * 阶梯价：同类型取“总价优惠最大”的活动，按数量命中最高档位单价。
	 */
	private void applyBestLadderPrice(PromotionContextDTO context, List<PromotionActivity> activities,
			PromotionCalculationVO result) {
		PromotionActivity best = null;
		List<PromotionCalculationVO.UnitPriceOverride> bestOverrides = null;
		BigDecimal bestSaving = BigDecimal.ZERO;
		JSONObject bestRules = null;
		for (PromotionActivity activity : activities) {
			try {
				JSONObject rules = JSONUtil.parseObj(activity.getRules());
				JSONArray ladders = rules.getJSONArray("ladders");
				if (ladders == null || ladders.isEmpty()) {
					continue;
				}
				Set<String> scopeSkus = parseScopeSkus(activity);
				List<PromotionCalculationVO.UnitPriceOverride> overrides = new ArrayList<>();
				BigDecimal saving = BigDecimal.ZERO;
				for (PromotionContextDTO.SkuItem item : context.getSkuItems()) {
					if (scopeSkus != null && !scopeSkus.isEmpty() && !scopeSkus.contains(item.getSkuId())) {
						continue;
					}
					PromotionCalculationVO.UnitPriceOverride override = resolveLadder(ladders, item);
					if (override != null) {
						override.setActivityId(activity.getId());
						overrides.add(override);
						saving = saving.add(override.getOriginalPrice().subtract(override.getUnitPrice())
								.multiply(BigDecimal.valueOf(item.getQuantity())));
					}
				}
				if (saving.compareTo(bestSaving) > 0) {
					bestSaving = saving;
					best = activity;
					bestOverrides = overrides;
					bestRules = rules;
				}
			}
			catch (Exception ex) {
				log.warn("阶梯价活动[{}]规则解析失败，跳过: {}", activity.getId(), ex.getMessage());
			}
		}
		if (best != null && bestOverrides != null && !bestOverrides.isEmpty()) {
			result.getLadderOverrides().addAll(bestOverrides);
			PromotionCalculationVO.ActivityDetail detail = new PromotionCalculationVO.ActivityDetail();
			detail.setActivityId(best.getId());
			detail.setActivityType(best.getActivityType());
			detail.setActivityName(best.getActivityName());
			detail.setDiscountAmount(bestSaving);
			detail.setRuleSnapshot(JSONUtil.toJsonStr(bestRules));
			result.getDetails().add(detail);
			result.setTotalDiscount(result.getTotalDiscount().add(bestSaving));
		}
	}

	private PromotionCalculationVO.UnitPriceOverride resolveLadder(JSONArray ladders,
			PromotionContextDTO.SkuItem item) {
		JSONObject bestLadder = null;
		Integer bestMinQty = null;
		for (Object element : ladders) {
			JSONObject ladder = (JSONObject) element;
			Integer minQty = ladder.getInt("minQty");
			BigDecimal unitPrice = ladder.getBigDecimal("unitPrice");
			if (minQty == null || unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
				continue;
			}
			if (item.getQuantity() >= minQty && unitPrice.compareTo(item.getSalesPrice()) < 0
					&& (bestMinQty == null || minQty > bestMinQty)) {
				bestMinQty = minQty;
				bestLadder = ladder;
			}
		}
		if (bestLadder == null) {
			return null;
		}
		PromotionCalculationVO.UnitPriceOverride override = new PromotionCalculationVO.UnitPriceOverride();
		override.setSkuId(item.getSkuId());
		override.setUnitPrice(bestLadder.getBigDecimal("unitPrice"));
		override.setOriginalPrice(item.getSalesPrice());
		return override;
	}

	/**
	 * 整船优惠：同类型取优惠金额最大者，按订单商品基价总额命中档位。
	 */
	private void applyBestWholeDiscount(PromotionContextDTO context, List<PromotionActivity> activities,
			PromotionCalculationVO result) {
		BigDecimal baseAmount = context.getSkuItems().stream()
			.map(item -> item.getSalesPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		PromotionActivity best = null;
		BigDecimal bestDiscount = BigDecimal.ZERO;
		JSONObject bestRules = null;
		for (PromotionActivity activity : activities) {
			try {
				JSONObject rules = JSONUtil.parseObj(activity.getRules());
				JSONArray tiers = rules.getJSONArray("tiers");
				if (tiers == null || tiers.isEmpty()) {
					continue;
				}
				BigDecimal discount = BigDecimal.ZERO;
				for (Object element : tiers) {
					JSONObject tier = (JSONObject) element;
					BigDecimal minAmount = tier.getBigDecimal("minAmount");
					BigDecimal discountAmount = tier.getBigDecimal("discountAmount");
					if (minAmount == null || discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
						continue;
					}
					if (baseAmount.compareTo(minAmount) >= 0 && discountAmount.compareTo(discount) > 0) {
						discount = discountAmount;
					}
				}
				if (discount.compareTo(bestDiscount) > 0) {
					bestDiscount = discount;
					best = activity;
					bestRules = rules;
				}
			}
			catch (Exception ex) {
				log.warn("整船优惠活动[{}]规则解析失败，跳过: {}", activity.getId(), ex.getMessage());
			}
		}
		if (best != null && bestDiscount.compareTo(BigDecimal.ZERO) > 0) {
			if (bestDiscount.compareTo(baseAmount) > 0) {
				bestDiscount = baseAmount;
			}
			result.setWholeDiscount(bestDiscount);
			PromotionCalculationVO.ActivityDetail detail = new PromotionCalculationVO.ActivityDetail();
			detail.setActivityId(best.getId());
			detail.setActivityType(best.getActivityType());
			detail.setActivityName(best.getActivityName());
			detail.setDiscountAmount(bestDiscount);
			detail.setRuleSnapshot(JSONUtil.toJsonStr(bestRules));
			result.getDetails().add(detail);
			result.setTotalDiscount(result.getTotalDiscount().add(bestDiscount));
		}
	}

	/**
	 * 范围匹配：scope_type 决定匹配键；purchase_scene 非空时额外限定。
	 */
	boolean matchesScope(PromotionActivity activity, PromotionContextDTO context) {
		if (StringUtils.hasText(activity.getPurchaseScene())
				&& !activity.getPurchaseScene().equals(context.getPurchaseScene())) {
			return false;
		}
		return switch (activity.getScopeType()) {
			case "1" -> true;
			case "2" -> {
				Set<String> scopeSkus = parseScopeSkus(activity);
				yield context.getSkuItems().stream().anyMatch(item -> scopeSkus.contains(item.getSkuId()));
			}
			case "4" -> activity.getScopeValue() != null
					&& activity.getScopeValue().equals(context.getPurchaseScene());
			case "5" -> containsValue(activity.getScopeValue(), context.getVesselId());
			case "6" -> containsValue(activity.getScopeValue(), context.getVesselCallId());
			case "7" -> activity.getScopeValue() != null
					&& activity.getScopeValue().equals(context.getPortCode());
			default -> false;
		};
	}

	private Set<String> parseScopeSkus(PromotionActivity activity) {
		if (!StringUtils.hasText(activity.getScopeValue())) {
			return Set.of();
		}
		return new HashSet<>(Arrays.asList(activity.getScopeValue().split(",")));
	}

	private boolean containsValue(String scopeValue, String value) {
		if (!StringUtils.hasText(scopeValue) || !StringUtils.hasText(value)) {
			return false;
		}
		return Arrays.asList(scopeValue.split(",")).contains(value);
	}


	/**
	 * 买赠：同类型取赠品数量最大者，按订单商品基价总额命中门槛；
	 * 赠品只入 gifts 列表，由订单侧追加 0 元明细，不混入付费数量。
	 */
	private void applyBestGift(PromotionContextDTO context, List<PromotionActivity> activities,
			PromotionCalculationVO result) {
		BigDecimal baseAmount = context.getSkuItems().stream()
			.map(item -> item.getSalesPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		PromotionActivity best = null;
		List<PromotionCalculationVO.GiftItem> bestGifts = null;
		for (PromotionActivity activity : activities) {
			try {
				JSONObject rules = JSONUtil.parseObj(activity.getRules());
				JSONArray giftArray = rules.getJSONArray("gifts");
				if (giftArray == null || giftArray.isEmpty()) {
					continue;
				}
				BigDecimal minAmount = rules.getBigDecimal("minAmount");
				if (minAmount == null || baseAmount.compareTo(minAmount) < 0) {
					continue;
				}
				List<PromotionCalculationVO.GiftItem> gifts = new ArrayList<>();
				int totalQty = 0;
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
					item.setActivityId(activity.getId());
					gifts.add(item);
					totalQty += quantity;
				}
				if (!gifts.isEmpty() && totalQty > (bestGifts == null ? 0
						: bestGifts.stream().mapToInt(PromotionCalculationVO.GiftItem::getQuantity).sum())) {
					best = activity;
					bestGifts = gifts;
				}
			}
			catch (Exception ex) {
				log.warn("买赠活动[{}]规则解析失败，跳过: {}", activity.getId(), ex.getMessage());
			}
		}
		if (best != null && bestGifts != null) {
			result.getGifts().addAll(bestGifts);
			PromotionCalculationVO.ActivityDetail detail = new PromotionCalculationVO.ActivityDetail();
			detail.setActivityId(best.getId());
			detail.setActivityType(best.getActivityType());
			detail.setActivityName(best.getActivityName());
			detail.setDiscountAmount(BigDecimal.ZERO);
			detail.setRuleSnapshot(best.getRules());
			result.getDetails().add(detail);
		}
	}

}