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
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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

	@DubboReference
	private com.aryn.cloud.promotion.api.remote.RemoteBuyerProfileService remoteBuyerProfileService;

	@DubboReference
	private com.aryn.cloud.product.api.remote.RemoteGoodsSkuService remoteGoodsSkuService;

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
										PromotionActivity.TYPE_GIFT, PromotionActivity.TYPE_NEWBIE_PRICE)));

		List<PromotionActivity> ladderHits = new ArrayList<>();
		List<PromotionActivity> wholeHits = new ArrayList<>();
		List<PromotionActivity> giftHits = new ArrayList<>();
		List<PromotionActivity> newbieHits = new ArrayList<>();
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
			else if (PromotionActivity.TYPE_NEWBIE_PRICE.equals(activity.getActivityType())) {
				newbieHits.add(activity);
			}
		}

		applyBestLadderPrice(context, ladderHits, result);
		applyBestWholeDiscount(context, wholeHits, result);
		applyBestGift(context, giftHits, result);
		applyBestNewbiePrice(context, newbieHits, result, new HashMap<>());
		return result;
	}

	/**
	 * 阶梯价：同类型取“总价优惠最大”的活动；规则按 SKU 配档（rules.skuLadders），
	 * 逐 SKU 按数量命中最高档位单价，未配档的 SKU 不改价。
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
				JSONArray skuLadders = rules.getJSONArray("skuLadders");
				if (skuLadders == null || skuLadders.isEmpty()) {
					continue;
				}
				List<PromotionCalculationVO.UnitPriceOverride> overrides = new ArrayList<>();
				BigDecimal saving = BigDecimal.ZERO;
				for (Object element : skuLadders) {
					JSONObject skuLadder = (JSONObject) element;
					String skuId = skuLadder.getStr("skuId");
					JSONArray ladders = skuLadder.getJSONArray("ladders");
					if (!StringUtils.hasText(skuId) || ladders == null || ladders.isEmpty()) {
						continue;
					}
					for (PromotionContextDTO.SkuItem item : context.getSkuItems()) {
						if (!skuId.equals(item.getSkuId())) {
							continue;
						}
						PromotionCalculationVO.UnitPriceOverride override = resolveLadder(ladders, item);
						if (override != null) {
							override.setActivityId(activity.getId());
							override.setActivityType(activity.getActivityType());
							overrides.add(override);
							saving = saving.add(override.getOriginalPrice().subtract(override.getUnitPrice())
									.multiply(BigDecimal.valueOf(item.getQuantity())));
						}
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
	 * 批量取候选买赠活动全部赠品的售价（价值比较用）；
	 * RPC 失败返回空映射，调用方降级为按赠品总数量取最优。
	 */
	private Map<String, BigDecimal> giftPrices(List<PromotionActivity> activities) {
		List<String> skuIds = new ArrayList<>();
		for (PromotionActivity activity : activities) {
			try {
				JSONArray giftArray = JSONUtil.parseObj(activity.getRules()).getJSONArray("gifts");
				if (giftArray == null) {
					continue;
				}
				for (Object element : giftArray) {
					String skuId = ((JSONObject) element).getStr("skuId");
					if (StringUtils.hasText(skuId)) {
						skuIds.add(skuId);
					}
				}
			}
			catch (Exception ex) {
				log.warn("买赠活动[{}]赠品SKU解析失败，跳过取其售价", activity.getId());
			}
		}
		if (skuIds.isEmpty()) {
			return Map.of();
		}
		try {
			return remoteGoodsSkuService.getBySkuIds(skuIds.stream().distinct().toList()).stream()
				.filter(sku -> sku.getSalesPrice() != null)
				.collect(Collectors.toMap(com.aryn.cloud.product.api.entity.GoodsSku::getId,
						com.aryn.cloud.product.api.entity.GoodsSku::getSalesPrice, (a, b) -> a));
		}
		catch (Exception ex) {
			log.warn("赠品售价查询失败，本次降级按赠品总数量取最优: {}", ex.getMessage());
			return Map.of();
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
			case "8" -> matchesFrequentScope(activity, context);
			default -> false;
		};
	}

	/**
	 * 常购专属价：用户常购 SKU 与活动指定 SKU 存在交集才命中；
	 * 查询失败按不命中处理（fail-closed，避免误给优惠）。
	 */
	private boolean matchesFrequentScope(PromotionActivity activity, PromotionContextDTO context) {
		try {
			Set<String> frequent = new HashSet<>(remoteBuyerProfileService.frequentSkuIds(
					context.getTenantId(), context.getUserId()));
			Set<String> scopeSkus = parseScopeSkus(activity);
			return context.getSkuItems().stream()
					.anyMatch(item -> frequent.contains(item.getSkuId()) && scopeSkus.contains(item.getSkuId()));
		}
		catch (Exception ex) {
			log.warn("常购画像查询失败，活动[{}]按不命中处理", activity.getId(), ex);
			return false;
		}
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
	 * 买赠：同类型取赠品价值最大者（售价 × 数量；售价查询失败降级为赠品总数量最大），
	 * 按订单商品基价总额命中门槛；赠品只入 gifts 列表，由订单侧追加 0 元明细，不混入付费数量。
	 */
	private void applyBestGift(PromotionContextDTO context, List<PromotionActivity> activities,
			PromotionCalculationVO result) {
		BigDecimal baseAmount = context.getSkuItems().stream()
			.map(item -> item.getSalesPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		Map<String, BigDecimal> giftPrices = giftPrices(activities);

		PromotionActivity best = null;
		List<PromotionCalculationVO.GiftItem> bestGifts = null;
		BigDecimal bestMetric = BigDecimal.ZERO;
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
				BigDecimal value = BigDecimal.ZERO;
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
					BigDecimal price = giftPrices.get(skuId);
					if (price != null) {
						value = value.add(price.multiply(BigDecimal.valueOf(quantity)));
					}
				}
				if (gifts.isEmpty()) {
					continue;
				}
				BigDecimal metric = giftPrices.isEmpty() ? BigDecimal.valueOf(totalQty) : value;
				if (metric.compareTo(bestMetric) > 0) {
					bestMetric = metric;
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

	/**
	 * 新人专享价：资格校验通过后按 rules.skus 逐 SKU 覆盖单价。
	 *
	 * <p>资格判定（本租户未成功支付过订单 + 注册 N 天内）走买家画像远程接口，
	 * 结果按 registerWithinDays 在本次 preview 内缓存，避免同请求多活动重复查库；
	 * 远程异常按非新人处理（fail-closed），不阻断结算。
	 *
	 * <p><b>命中口径必须与「是否降价」解耦</b>：下单链会对同一订单调用两次 preview
	 * （applyPromotionLadder 改基价一次、reserve 落锁一次），第二次传入的 salesPrice
	 * 已是新人价。若按「活动价低于当前基价」判命中，第二次就会判成未命中 →
	 * details 为空 → 锁不写入 → 在途唯一与实际优惠都失效（真实缺陷，仅端到端可现）。
	 * 故命中只要求「资格通过 且 规则中配置了该 SKU」，是否产生覆盖另判。
	 *
	 * @param eligibilityCache 本次 preview 内 registerWithinDays → 是否具备资格
	 */
	private void applyBestNewbiePrice(PromotionContextDTO context, List<PromotionActivity> activities,
			PromotionCalculationVO result, Map<Integer, Boolean> eligibilityCache) {
		if (activities.isEmpty() || !StringUtils.hasText(context.getUserId())) {
			return;
		}
		PromotionActivity best = null;
		JSONObject bestRules = null;
		BigDecimal bestSaving = BigDecimal.ZERO;
		for (PromotionActivity activity : activities) {
			try {
				JSONObject rules = JSONUtil.parseObj(activity.getRules());
				JSONArray newbieSkus = rules.getJSONArray("skus");
				if (newbieSkus == null || newbieSkus.isEmpty()) {
					continue;
				}
				if (!eligibleAsNewbie(context, rules.getInt("registerWithinDays"), eligibilityCache)) {
					continue;
				}
				BigDecimal saving = newbieSaving(context, newbieSkus);
				// 资格通过即视为命中（saving 为 0 表示本次基价已等于活动价，即重放场景）；
				// 多活动并存时仍取节省额最大者，节省额相同时取先出现的（稳定）
				if (best == null || saving.compareTo(bestSaving) > 0) {
					bestSaving = saving;
					best = activity;
					bestRules = rules;
				}
			}
			catch (Exception ex) {
				log.warn("新人专享活动[{}]规则解析失败，跳过: {}", activity.getId(), ex.getMessage());
			}
		}
		if (best != null) {
			mergeNewbieOverrides(context, best, bestRules, result);
		}
	}

	/** 活动命中 SKU 相对当前基价的总节省额（仅统计真正降价的 SKU）。 */
	private BigDecimal newbieSaving(PromotionContextDTO context, JSONArray newbieSkus) {
		BigDecimal saving = BigDecimal.ZERO;
		for (Object element : newbieSkus) {
			JSONObject sku = (JSONObject) element;
			String skuId = sku.getStr("skuId");
			BigDecimal price = sku.getBigDecimal("price");
			if (!StringUtils.hasText(skuId) || price == null || price.compareTo(BigDecimal.ZERO) < 0) {
				continue;
			}
			for (PromotionContextDTO.SkuItem item : context.getSkuItems()) {
				if (skuId.equals(item.getSkuId()) && price.compareTo(item.getSalesPrice()) < 0) {
					saving = saving.add(item.getSalesPrice().subtract(price)
							.multiply(BigDecimal.valueOf(item.getQuantity())));
				}
			}
		}
		return saving;
	}

	/**
	 * 将命中活动的新人价覆盖合并进结果：同 SKU 与阶梯价比较取更低价，避免改价链出现
	 * 后写覆盖先写的随机结果（applyPromotionLadder 按 skuId 逐条 findFirst 应用）。
	 *
	 * <p>只要活动命中了本单的任一 SKU 就必须产出 activityDetail（即使 saving 为 0）：
	 * 下单链的第二次 preview（reserve 落锁）必须看到 detail 才会写 promotion_lock，
	 * 否则在途唯一与优惠快照都会静默丢失。
	 *
	 * @return 是否命中（命中=活动覆盖了本单至少一个 SKU）
	 */
	private boolean mergeNewbieOverrides(PromotionContextDTO context, PromotionActivity activity, JSONObject rules,
			PromotionCalculationVO result) {
		JSONArray newbieSkus = rules.getJSONArray("skus");
		BigDecimal saving = BigDecimal.ZERO;
		boolean matched = false;
		for (Object element : newbieSkus) {
			JSONObject sku = (JSONObject) element;
			String skuId = sku.getStr("skuId");
			BigDecimal price = sku.getBigDecimal("price");
			if (!StringUtils.hasText(skuId) || price == null || price.compareTo(BigDecimal.ZERO) < 0) {
				continue;
			}
			for (PromotionContextDTO.SkuItem item : context.getSkuItems()) {
				if (!skuId.equals(item.getSkuId())) {
					continue;
				}
				matched = true;
				// 当前基价已不高出活动价（重放/已改价）时不重复覆盖，但活动仍算命中
				if (price.compareTo(item.getSalesPrice()) >= 0) {
					continue;
				}
				// 已存在同 SKU 覆盖（阶梯价）时取更低价，保留胜出方活动ID
				PromotionCalculationVO.UnitPriceOverride existing = result.getLadderOverrides().stream()
					.filter(override -> override.getSkuId().equals(skuId))
					.findFirst()
					.orElse(null);
				if (existing != null && existing.getUnitPrice().compareTo(price) <= 0) {
					continue;
				}
				if (existing != null) {
					result.getLadderOverrides().remove(existing);
				}
				PromotionCalculationVO.UnitPriceOverride override = new PromotionCalculationVO.UnitPriceOverride();
				override.setSkuId(skuId);
				override.setUnitPrice(price);
				override.setOriginalPrice(item.getSalesPrice());
				override.setActivityId(activity.getId());
				override.setActivityType(activity.getActivityType());
				result.getLadderOverrides().add(override);
				saving = saving.add(item.getSalesPrice().subtract(price)
						.multiply(BigDecimal.valueOf(item.getQuantity())));
			}
		}
		if (!matched) {
			return false;
		}
		PromotionCalculationVO.ActivityDetail detail = new PromotionCalculationVO.ActivityDetail();
		detail.setActivityId(activity.getId());
		detail.setActivityType(activity.getActivityType());
		detail.setActivityName(activity.getActivityName());
		detail.setDiscountAmount(saving);
		detail.setRuleSnapshot(JSONUtil.toJsonStr(rules));
		result.getDetails().add(detail);
		if (saving.compareTo(BigDecimal.ZERO) > 0) {
			result.setTotalDiscount(result.getTotalDiscount().add(saving));
		}
		return true;
	}

	/**
	 * 资格判定（本次 preview 内按 registerWithinDays 缓存）；未登录或异常按非新人（fail-closed）。
	 */
	private boolean eligibleAsNewbie(PromotionContextDTO context, Integer registerWithinDays,
			Map<Integer, Boolean> eligibilityCache) {
		return eligibilityCache.computeIfAbsent(registerWithinDays, days -> {
			try {
				com.aryn.cloud.promotion.api.vo.NewbieEligibilityVO eligibility = remoteBuyerProfileService
					.checkNewbieEligibility(context.getTenantId(), context.getUserId(), days);
				return eligibility != null && Boolean.TRUE.equals(eligibility.getEligible());
			}
			catch (Exception ex) {
				log.warn("新人资格查询失败，按非新人处理 tenant={} user={}: {}", context.getTenantId(),
						context.getUserId(), ex.getMessage());
				return false;
			}
		});
	}

}