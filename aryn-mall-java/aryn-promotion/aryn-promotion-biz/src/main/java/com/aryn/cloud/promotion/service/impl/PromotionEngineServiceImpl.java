package com.aryn.cloud.promotion.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.vo.PromotionCalculationVO;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.service.PromotionEngineService;
import com.aryn.cloud.user.api.vo.MemberLevelSnapshotVO;
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

	private final PromotionLockUsage promotionLockUsage;

	@DubboReference
	private com.aryn.cloud.promotion.api.remote.RemoteBuyerProfileService remoteBuyerProfileService;

	@DubboReference
	private com.aryn.cloud.product.api.remote.RemoteGoodsSkuService remoteGoodsSkuService;

	/**
	 * 会员等级快照走 user 域 Dubbo 接口（定义在 user-api、实现挂 user-biz）。
	 *
	 * <p>必须用<b>非 final 字段</b>+@DubboReference（构造器注入远程接口曾在 boot 启动失败），
	 * 与 remoteBuyerProfileService 同款写法。等级查询失败一律按无资格处理（fail-closed）。
	 */
	@DubboReference
	private com.aryn.cloud.user.api.remote.RemoteMemberLevelService remoteMemberLevelService;

	/**
	 * 试算：额度（预算/每人限享/赠品总量）不足的活动不参与计算。
	 *
	 * <p>剔除后重新计算，保证「确认页展示的价格 = 下单可成交的价格」；
	 * 额度不再是整单拦截器，活动失效后订单按其余优惠或原价继续。
	 * 并发窗口由 reserve 的原子扣减兜底。
	 */
	@Override
	public PromotionCalculationVO preview(PromotionContextDTO context) {
		if (context == null || context.getTenantId() == null || context.getSkuItems() == null
				|| context.getSkuItems().isEmpty()) {
			return emptyResult();
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
										PromotionActivity.TYPE_GIFT, PromotionActivity.TYPE_NEWBIE_PRICE,
										PromotionActivity.TYPE_MEMBER_PRICE, PromotionActivity.TYPE_CLEARANCE)));
		Map<String, PromotionActivity> activityById = activities.stream()
			.collect(Collectors.toMap(PromotionActivity::getId, activity -> activity, (left, right) -> left));
		Set<String> excluded = new HashSet<>();
		Map<String, Long> userUsage = new HashMap<>();
		Map<String, Integer> giftUsage = new HashMap<>();
		// 会员等级快照按本次 preview 缓存：额度重算会多次调用 calculate，缓存须跨轮复用，
		// 避免同一请求对同一用户/等级重复打 Dubbo。
		Map<String, MemberLevelSnapshotVO> memberLevelCache = new HashMap<>();
		while (true) {
			List<PromotionActivity> candidates = activities.stream()
				.filter(activity -> !excluded.contains(activity.getId()))
				.toList();
			PromotionCalculationVO result = calculate(context, candidates, memberLevelCache);
			PromotionActivity exhausted = firstExhausted(context, result, activityById, userUsage, giftUsage);
			if (exhausted == null || !excluded.add(exhausted.getId())) {
				return result;
			}
			log.info("活动[{}]额度已用尽，本次试算不参与 userId={}", exhausted.getId(), context.getUserId());
		}
	}

	private PromotionCalculationVO emptyResult() {
		PromotionCalculationVO result = new PromotionCalculationVO();
		result.setTotalDiscount(BigDecimal.ZERO);
		result.setWholeDiscount(BigDecimal.ZERO);
		result.setLadderOverrides(new ArrayList<>());
		result.setDetails(new ArrayList<>());
		result.setGifts(new ArrayList<>());
		return result;
	}

	private PromotionCalculationVO calculate(PromotionContextDTO context, List<PromotionActivity> activities,
			Map<String, MemberLevelSnapshotVO> memberLevelCache) {
		PromotionCalculationVO result = emptyResult();
		List<PromotionActivity> ladderHits = new ArrayList<>();
		List<PromotionActivity> wholeHits = new ArrayList<>();
		List<PromotionActivity> giftHits = new ArrayList<>();
		List<PromotionActivity> memberHits = new ArrayList<>();
		List<PromotionActivity> clearanceHits = new ArrayList<>();
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
			else if (PromotionActivity.TYPE_MEMBER_PRICE.equals(activity.getActivityType())) {
				memberHits.add(activity);
			}
			else if (PromotionActivity.TYPE_CLEARANCE.equals(activity.getActivityType())) {
				clearanceHits.add(activity);
			}
			else if (PromotionActivity.TYPE_NEWBIE_PRICE.equals(activity.getActivityType())) {
				newbieHits.add(activity);
			}
		}

		applyBestLadderPrice(context, ladderHits, result);
		// 会员价/出清价同为 stackPriority=2：在阶梯价之后、新人价之前统一落价，同 SKU 取低。
		// 返回「已被身份价锁定的 SKU → 实际生效价」：新人价与其取低（2026-11-08 语义调整，
		// 原为身份价无条件优先——¥1 新人钩子被全店会员价压制永不生效），同价身份价保留。
		Map<String, BigDecimal> identityPricedPrices = applyBestIdentityPrice(context, memberHits, clearanceHits,
				result, memberLevelCache);
		// 新人价必须先于整船优惠落定：整船门槛要排除已享新人价（及会员价/出清价）的 SKU
		applyBestNewbiePrice(context, newbieHits, result, new HashMap<>(), identityPricedPrices);
		applyBestWholeDiscount(context, wholeHits, result);
		applyBestGift(context, giftHits, result);
		return result;
	}

	private PromotionActivity firstExhausted(PromotionContextDTO context, PromotionCalculationVO result,
			Map<String, PromotionActivity> activityById, Map<String, Long> userUsage, Map<String, Integer> giftUsage) {
		for (PromotionCalculationVO.ActivityDetail detail : result.getDetails()) {
			PromotionActivity activity = activityById.get(detail.getActivityId());
			if (activity == null) {
				continue;
			}
			if (budgetExhausted(activity, detail.getDiscountAmount())) {
				return activity;
			}
			if (perUserExhausted(context, activity, userUsage)) {
				return activity;
			}
			if (giftStockExhausted(context, activity, result, giftUsage)) {
				return activity;
			}
		}
		return null;
	}

	private boolean budgetExhausted(PromotionActivity activity, BigDecimal currentDiscount) {
		if (activity.getTotalBudget() == null || activity.getTotalBudget().compareTo(BigDecimal.ZERO) <= 0) {
			return false;
		}
		BigDecimal current = currentDiscount == null ? BigDecimal.ZERO : currentDiscount;
		if (current.compareTo(BigDecimal.ZERO) <= 0) {
			// 买赠/重放场景本次不再新增让利，不占预算
			return false;
		}
		BigDecimal consumed = activity.getConsumedBudget() == null ? BigDecimal.ZERO : activity.getConsumedBudget();
		return consumed.add(current).compareTo(activity.getTotalBudget()) > 0;
	}

	private boolean perUserExhausted(PromotionContextDTO context, PromotionActivity activity,
			Map<String, Long> userUsage) {
		if (activity.getPerUserLimit() == null || activity.getPerUserLimit() <= 0
				|| !StringUtils.hasText(context.getUserId())) {
			return false;
		}
		long used = userUsage.computeIfAbsent(activity.getId(),
				id -> promotionLockUsage.countUserLocks(context.getTenantId(), context.getUserId(), id));
		return used >= activity.getPerUserLimit();
	}

	private boolean giftStockExhausted(PromotionContextDTO context, PromotionActivity activity,
			PromotionCalculationVO result, Map<String, Integer> giftUsage) {
		if (!PromotionActivity.TYPE_GIFT.equals(activity.getActivityType())
				|| activity.getGiftStock() == null || activity.getGiftStock() <= 0) {
			return false;
		}
		int current = result.getGifts().stream()
			.filter(gift -> activity.getId().equals(gift.getActivityId()))
			.mapToInt(PromotionCalculationVO.GiftItem::getQuantity)
			.sum();
		int used = giftUsage.computeIfAbsent(activity.getId(),
				id -> promotionLockUsage.sumGiftQuantity(context.getTenantId(), id));
		return used + current > activity.getGiftStock();
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
							fillStackPolicy(override, activity);
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
	 *
	 * <p>已享活动价（拼团/秒杀/限时折扣等外部活动价，以及本引擎产生的阶梯价/会员价/新人价）
	 * 的 SKU 不计入门槛、也不参与后续分摊，避免活动价商品被整船优惠二次让利。
	 */
	private void applyBestWholeDiscount(PromotionContextDTO context, List<PromotionActivity> activities,
			PromotionCalculationVO result) {
		Set<String> activityPricedSkuIds = context.getSkuItems().stream()
			.filter(item -> Boolean.TRUE.equals(item.getActivityPriced()))
			.map(PromotionContextDTO.SkuItem::getSkuId)
			.collect(Collectors.toSet());
		result.getLadderOverrides().forEach(override -> activityPricedSkuIds.add(override.getSkuId()));
		BigDecimal baseAmount = context.getSkuItems().stream()
			.filter(item -> !activityPricedSkuIds.contains(item.getSkuId()))
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
			// 可参与商品为 0 时不产生整船优惠明细，避免落一条 0 元锁
			if (bestDiscount.compareTo(BigDecimal.ZERO) > 0) {
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
	 * 身份价（会员价 + 出清价）：在阶梯价之后、新人价之前统一落价。
	 *
	 * <p>会员价：登录用户经 user 域 Dubbo 查当前等级 {sortOrder} 与活动门槛等级
	 * {minLevelId → sortOrder}，用户等级 ≥ 门槛等级才可享；未登录/无等级/任一侧查询异常
	 * 一律按无资格处理（fail-closed），不阻断结算。等级快照按本次 preview 内缓存，
	 * 避免同请求多活动重复查库（不做跨请求缓存：等级随消费/积分变化，缓存会延迟权益生效）。
	 *
	 * <p>出清价：免登录，按 SKU 配置价生效；计算时二次校验 expireTime，已到期不参与
	 * （即使兜底 Job 尚未把该 SKU 从系统活动移除）。
	 *
	 * <p><b>多活动按 SKU 取优</b>：历史手工活动已下线，当前仅系统托管活动 SYS_MEMBER_ZONE；
	 * 合并逻辑保留兜底，不能只挑“节省额最大”的一个活动，否则未被该活动覆盖的标签商品会丢会员价。
	 * 同一 SKU 上历史手工活动（存量数据）优先于系统活动，同类活动之间取最低价；
	 * 出清价与会员价之间取最低价。
	 *
	 * <p>同 SKU 已有阶梯价覆盖时取更低价（阶梯价更低则保留阶梯价，避免重复让利与
	 * 同 SKU 多条覆盖）；身份价不低于当前基价（倒挂）时不产生覆盖。
	 *
	 * @param memberLevelCache 本次 preview 内 用户/等级 → 等级快照（含 null，fail-closed 缓存）
	 * @return 已被身份价锁定的 SKU 集合：这些 SKU 的新人价必须跳过（身份价优先），
	 *         包含「价格低于基价」的 SKU，即使最终保留的是更低的阶梯价
	 */
	private Map<String, BigDecimal> applyBestIdentityPrice(PromotionContextDTO context,
			List<PromotionActivity> memberActivities,
			List<PromotionActivity> clearanceActivities, PromotionCalculationVO result,
			Map<String, MemberLevelSnapshotVO> memberLevelCache) {
		List<PriceOffer> memberOffers = memberOffers(context, memberActivities, memberLevelCache);
		List<PriceOffer> clearanceOffers = clearanceOffers(context, clearanceActivities);
		if (memberOffers.isEmpty() && clearanceOffers.isEmpty()) {
			return Map.of();
		}
		return mergeIdentityOverrides(context, memberOffers, clearanceOffers, result);
	}

	/** 所有命中的会员价活动（手工 + 系统），逐 SKU 由合并阶段取优。 */
	private List<PriceOffer> memberOffers(PromotionContextDTO context, List<PromotionActivity> activities,
			Map<String, MemberLevelSnapshotVO> memberLevelCache) {
		if (activities.isEmpty() || !StringUtils.hasText(context.getUserId())) {
			return List.of();
		}
		List<PriceOffer> offers = new ArrayList<>();
		for (PromotionActivity activity : activities) {
			try {
				JSONObject rules = JSONUtil.parseObj(activity.getRules());
				JSONArray skus = rules.getJSONArray("skus");
				if (skus == null || skus.isEmpty()) {
					continue;
				}
				String minLevelId = rules.getStr("minLevelId");
				if (!StringUtils.hasText(minLevelId)) {
					continue;
				}
				if (!eligibleAsMember(context, minLevelId, memberLevelCache)) {
					continue;
				}
				offers.add(new PriceOffer(activity, rules, parsePriceBySku(skus, "unitPrice")));
			}
			catch (Exception ex) {
				log.warn("会员价活动[{}]规则解析失败，跳过: {}", activity.getId(), ex.getMessage());
			}
		}
		return offers;
	}

	private boolean isSystemActivity(PromotionActivity activity) {
		return activity != null && StringUtils.hasText(activity.getActivityCode())
				&& activity.getActivityCode().startsWith("SYS_");
	}

	/** 所有命中的出清价活动（通常只有系统托管一条，保留多活动取优能力）。 */
	private List<PriceOffer> clearanceOffers(PromotionContextDTO context,
			List<PromotionActivity> activities) {
		if (activities.isEmpty()) {
			return List.of();
		}
		LocalDateTime now = context.getActivityTime() != null ? context.getActivityTime() : LocalDateTime.now();
		List<PriceOffer> offers = new ArrayList<>();
		for (PromotionActivity activity : activities) {
			try {
				JSONObject rules = JSONUtil.parseObj(activity.getRules());
				JSONArray skus = rules.getJSONArray("skus");
				if (skus == null || skus.isEmpty()) {
					continue;
				}
				Map<String, BigDecimal> priceBySku = parseClearancePriceBySku(skus, now);
				if (!priceBySku.isEmpty()) {
					offers.add(new PriceOffer(activity, rules, priceBySku));
				}
			}
			catch (Exception ex) {
				log.warn("出清价活动[{}]规则解析失败，跳过: {}", activity.getId(), ex.getMessage());
			}
		}
		return offers;
	}

	/** rules.skus 的 SKU → 价格映射（保持配置顺序）；价格非正数的条目忽略。 */
	private Map<String, BigDecimal> parsePriceBySku(JSONArray skus, String priceKey) {
		Map<String, BigDecimal> priceBySku = new java.util.LinkedHashMap<>();
		for (Object element : skus) {
			JSONObject sku = (JSONObject) element;
			String skuId = sku.getStr("skuId");
			BigDecimal price = sku.getBigDecimal(priceKey);
			if (StringUtils.hasText(skuId) && price != null && price.signum() > 0) {
				priceBySku.put(skuId, price);
			}
		}
		return priceBySku;
	}

	/** 出清价映射：只保留未到期且价格为正的 SKU，到期商品绝不落价。 */
	private Map<String, BigDecimal> parseClearancePriceBySku(JSONArray skus, LocalDateTime now) {
		Map<String, BigDecimal> priceBySku = new java.util.LinkedHashMap<>();
		for (Object element : skus) {
			JSONObject sku = (JSONObject) element;
			String skuId = sku.getStr("skuId");
			BigDecimal price = sku.getBigDecimal("price");
			if (StringUtils.hasText(skuId) && price != null && price.signum() > 0 && !isClearanceExpired(sku, now)) {
				priceBySku.put(skuId, price);
			}
		}
		return priceBySku;
	}

	private boolean isClearanceExpired(JSONObject sku, LocalDateTime now) {
		String expireTime = sku.getStr("expireTime");
		if (!StringUtils.hasText(expireTime)) {
			return false;
		}
		try {
			return !LocalDateTime.parse(expireTime).isAfter(now);
		}
		catch (Exception ex) {
			log.warn("出清价 expireTime 解析失败，按已到期处理: {}", expireTime);
			return true;
		}
	}

	/**
	 * 身份价覆盖合并：逐 SKU 在全部会员活动与出清活动中取最低价，再与阶梯价比较取更低价，
	 * 逐 SKU 生成一条覆盖，避免改价链（applyPromotionLadder 按 skuId findFirst）出现随机覆盖。
	 *
	 * <p>价格低于基价即落价并计入总让利；阶梯价更低时保留阶梯价（用户更优），
	 * 但该 SKU 仍计入锁定集合供新人价取低比较。倒挂（价格 ≥ 基价）的 SKU
	 * 不锁定，新人价仍可对其生效。每个参与的活动各自产出明细，同 SKU 的让利只归胜出方，
	 * 不会重复计算。
	 *
	 * @return 已被身份价锁定的 SKU → 实际生效价（阶梯价保留时为阶梯价），供新人价取低比较
	 */
	private Map<String, BigDecimal> mergeIdentityOverrides(PromotionContextDTO context, List<PriceOffer> memberOffers,
			List<PriceOffer> clearanceOffers, PromotionCalculationVO result) {
		Map<String, BigDecimal> lockedPrices = new java.util.LinkedHashMap<>();
		Map<String, BigDecimal> savingByActivity = new java.util.LinkedHashMap<>();
		Map<String, PromotionActivity> participating = new java.util.LinkedHashMap<>();
		for (PromotionContextDTO.SkuItem item : context.getSkuItems()) {
			IdentityCandidate winner = chooseIdentityPrice(memberOffers, clearanceOffers, item);
			if (winner == null) {
				continue;
			}
			participating.putIfAbsent(winner.activity().getId(), winner.activity());
			PromotionCalculationVO.UnitPriceOverride existing = findOverride(result, item.getSkuId());
			if (existing != null && existing.getUnitPrice().compareTo(winner.price()) <= 0) {
				// 阶梯价更低：保留阶梯价（用户更优），锁定价记阶梯价——新人价与实际生效价取低
				lockedPrices.put(item.getSkuId(), existing.getUnitPrice());
				continue;
			}
			if (existing != null) {
				result.getLadderOverrides().remove(existing);
			}
			PromotionCalculationVO.UnitPriceOverride override = new PromotionCalculationVO.UnitPriceOverride();
			override.setSkuId(item.getSkuId());
			override.setUnitPrice(winner.price());
			override.setOriginalPrice(item.getSalesPrice());
			override.setActivityId(winner.activity().getId());
			override.setActivityType(winner.activity().getActivityType());
			fillStackPolicy(override, winner.activity());
			result.getLadderOverrides().add(override);
			lockedPrices.put(item.getSkuId(), winner.price());
			savingByActivity.merge(winner.activity().getId(),
				item.getSalesPrice().subtract(winner.price())
					.multiply(BigDecimal.valueOf(item.getQuantity())),
				BigDecimal::add);
		}
		if (lockedPrices.isEmpty()) {
			return Map.of();
		}
		appendIdentityDetails(memberOffers, participating, savingByActivity, result);
		appendIdentityDetails(clearanceOffers, participating, savingByActivity, result);
		return lockedPrices;
	}

	/** 身份价同 SKU 取低：同价时优先会员价（身份权益稳定归属）。 */
	private IdentityCandidate chooseIdentityPrice(List<PriceOffer> memberOffers, List<PriceOffer> clearanceOffers,
			PromotionContextDTO.SkuItem item) {
		IdentityCandidate member = bestMemberCandidate(memberOffers, item);
		IdentityCandidate clearance = bestOfferCandidate(clearanceOffers, item);
		if (member == null) {
			return clearance;
		}
		if (clearance == null) {
			return member;
		}
		return clearance.price().compareTo(member.price()) < 0 ? clearance : member;
	}

	/**
	 * 会员价候选：历史手工活动已下线，当前仅系统托管活动 SYS_MEMBER_ZONE；
	 * 同一 SKU 上历史手工活动（存量数据）优先于系统托管活动，同类活动之间取最低价。
	 */
	private IdentityCandidate bestMemberCandidate(List<PriceOffer> offers, PromotionContextDTO.SkuItem item) {
		IdentityCandidate manual = null;
		IdentityCandidate system = null;
		for (PriceOffer offer : offers) {
			IdentityCandidate candidate = candidate(offer, item);
			if (candidate == null) {
				continue;
			}
			if (isSystemActivity(offer.activity())) {
				if (system == null || candidate.price().compareTo(system.price()) < 0) {
					system = candidate;
				}
			}
			else if (manual == null || candidate.price().compareTo(manual.price()) < 0) {
				manual = candidate;
			}
		}
		return manual != null ? manual : system;
	}

	/** 出清价候选：多活动时取最低价。 */
	private IdentityCandidate bestOfferCandidate(List<PriceOffer> offers, PromotionContextDTO.SkuItem item) {
		IdentityCandidate best = null;
		for (PriceOffer offer : offers) {
			IdentityCandidate candidate = candidate(offer, item);
			if (candidate != null && (best == null || candidate.price().compareTo(best.price()) < 0)) {
				best = candidate;
			}
		}
		return best;
	}

	private IdentityCandidate candidate(PriceOffer offer, PromotionContextDTO.SkuItem item) {
		if (offer == null) {
			return null;
		}
		BigDecimal price = offer.priceBySku().get(item.getSkuId());
		if (price == null || price.signum() <= 0 || price.compareTo(item.getSalesPrice()) >= 0) {
			return null;
		}
		return new IdentityCandidate(offer.activity(), price);
	}

	private void appendIdentityDetails(List<PriceOffer> offers, Map<String, PromotionActivity> participating,
			Map<String, BigDecimal> savingByActivity, PromotionCalculationVO result) {
		for (PriceOffer offer : offers) {
			if (!participating.containsKey(offer.activity().getId())) {
				continue;
			}
			BigDecimal saving = savingByActivity.getOrDefault(offer.activity().getId(), BigDecimal.ZERO);
			PromotionCalculationVO.ActivityDetail detail = new PromotionCalculationVO.ActivityDetail();
			detail.setActivityId(offer.activity().getId());
			detail.setActivityType(offer.activity().getActivityType());
			detail.setActivityName(offer.activity().getActivityName());
			detail.setDiscountAmount(saving);
			detail.setRuleSnapshot(JSONUtil.toJsonStr(offer.rules()));
			result.getDetails().add(detail);
			if (saving.compareTo(BigDecimal.ZERO) > 0) {
				result.setTotalDiscount(result.getTotalDiscount().add(saving));
			}
		}
	}

	private PromotionCalculationVO.UnitPriceOverride findOverride(PromotionCalculationVO result, String skuId) {
		return result.getLadderOverrides()
			.stream()
			.filter(override -> override.getSkuId().equals(skuId))
			.findFirst()
			.orElse(null);
	}

	/**
	 * 会员价资格：用户等级 sortOrder ≥ 活动门槛等级 sortOrder；未登录/无等级/任一侧查询异常
	 * 一律不合格（fail-closed，避免越级白送权益）。等级快照按本次 preview 缓存。
	 */
	private boolean eligibleAsMember(PromotionContextDTO context, String minLevelId,
			Map<String, MemberLevelSnapshotVO> cache) {
		MemberLevelSnapshotVO userLevel = userLevel(context.getTenantId(), context.getUserId(), cache);
		if (userLevel == null || userLevel.getSortOrder() == null) {
			return false;
		}
		MemberLevelSnapshotVO requiredLevel = level(context.getTenantId(), minLevelId, cache);
		if (requiredLevel == null || requiredLevel.getSortOrder() == null) {
			return false;
		}
		return userLevel.getSortOrder() >= requiredLevel.getSortOrder();
	}

	/** 用户等级快照（本次 preview 缓存，含 null；异常按无等级缓存，fail-closed）。 */
	private MemberLevelSnapshotVO userLevel(String tenantId, String userId,
			Map<String, MemberLevelSnapshotVO> cache) {
		String cacheKey = "user:" + userId;
		if (cache.containsKey(cacheKey)) {
			return cache.get(cacheKey);
		}
		MemberLevelSnapshotVO level = null;
		try {
			level = remoteMemberLevelService.getUserLevel(tenantId, userId);
		}
		catch (Exception ex) {
			log.warn("会员价资格查询用户[{}]等级失败，按无资格处理: {}", userId, ex.getMessage());
		}
		cache.put(cacheKey, level);
		return level;
	}

	/** 活动门槛等级快照（本次 preview 缓存，含 null；异常按无门槛缓存，fail-closed）。 */
	private MemberLevelSnapshotVO level(String tenantId, String levelId,
			Map<String, MemberLevelSnapshotVO> cache) {
		String cacheKey = "level:" + levelId;
		if (cache.containsKey(cacheKey)) {
			return cache.get(cacheKey);
		}
		MemberLevelSnapshotVO level = null;
		try {
			level = remoteMemberLevelService.getLevelById(tenantId, levelId);
		}
		catch (Exception ex) {
			log.warn("会员价资格查询等级[{}]失败，按无资格处理: {}", levelId, ex.getMessage());
		}
		cache.put(cacheKey, level);
		return level;
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
	 * <p><b>身份价取低</b>（2026-11-08 语义调整，原为身份价无条件优先）：已被身份价
	 * （会员/出清，含保留的阶梯价）锁定的 SKU 不再跳过，新人价与实际生效价比较，
	 * <b>严格更低才覆盖</b>——¥1 新人钩子不再被全店会员价吞掉；同价身份价保留。
	 * 被压制的 SKU 不计入节省额也不构成命中（不产 detail、不写新人锁，
	 * 避免新人价没生效却白白消耗一次性新人资格）。
	 *
	 * @param eligibilityCache      本次 preview 内 registerWithinDays → 是否具备资格
	 * @param identityPricedPrices  已被身份价锁定的 SKU → 实际生效价（新人价取低比较基准）
	 */
	private void applyBestNewbiePrice(PromotionContextDTO context, List<PromotionActivity> activities,
			PromotionCalculationVO result, Map<Integer, Boolean> eligibilityCache,
			Map<String, BigDecimal> identityPricedPrices) {
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
				BigDecimal saving = newbieSaving(context, newbieSkus, identityPricedPrices);
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
			mergeNewbieOverrides(context, best, bestRules, result, identityPricedPrices);
		}
	}

	/**
	 * 活动命中 SKU 相对当前生效价的总节省额（仅统计真正降价的 SKU）。
	 *
	 * <p>被身份价锁定的 SKU 以「min(基价, 身份价)」为比较基准：新人价低于它才计节省
	 * （该 SKU 的新人价合并时才会真正生效），与 {@link #mergeNewbieOverrides} 的取低口径一致。
	 */
	private BigDecimal newbieSaving(PromotionContextDTO context, JSONArray newbieSkus,
			Map<String, BigDecimal> identityPricedPrices) {
		BigDecimal saving = BigDecimal.ZERO;
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
				BigDecimal identityPrice = identityPricedPrices.get(skuId);
				BigDecimal effectiveBase = identityPrice != null
						&& identityPrice.compareTo(item.getSalesPrice()) < 0
								? identityPrice
								: item.getSalesPrice();
				if (price.compareTo(effectiveBase) < 0) {
					saving = saving.add(effectiveBase.subtract(price)
							.multiply(BigDecimal.valueOf(item.getQuantity())));
				}
			}
		}
		return saving;
	}

	/**
	 * 将命中活动的新人价覆盖合并进结果：同 SKU 与已有覆盖（阶梯价/身份价）比较取更低价，
	 * 避免改价链出现后写覆盖先写的随机结果（applyPromotionLadder 按 skuId 逐条 findFirst 应用）。
	 *
	 * <p>只要活动「真正生效」于本单任一 SKU（产生或本就持有覆盖）就必须产出
	 * activityDetail（即使 saving 为 0）：下单链的第二次 preview（reserve 落锁）必须看到
	 * detail 才会写 promotion_lock，否则在途唯一与优惠快照都会静默丢失。
	 *
	 * <p><b>身份价取低</b>（2026-11-08 语义调整）：身份价/阶梯价不高于新人价的 SKU 保留
	 * 原覆盖且<b>不构成命中</b>——新人价没生效就不写锁，避免白白消耗一次性新人资格。
	 *
	 * @return 是否命中（命中=活动对本单至少一个 SKU 的实际生效价做出了贡献）
	 */
	private boolean mergeNewbieOverrides(PromotionContextDTO context, PromotionActivity activity, JSONObject rules,
			PromotionCalculationVO result, Map<String, BigDecimal> identityPricedPrices) {
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
				// 已存在同 SKU 覆盖（阶梯价/身份价）时取更低价；不高于新人价则保留原覆盖，
				// 且该 SKU 不构成命中（新人价未生效，不写锁消耗资格）
				PromotionCalculationVO.UnitPriceOverride existing = result.getLadderOverrides().stream()
					.filter(override -> override.getSkuId().equals(skuId))
					.findFirst()
					.orElse(null);
				if (existing != null && existing.getUnitPrice().compareTo(price) <= 0) {
					continue;
				}
				matched = true;
				// 当前基价已不高出活动价（重放/已改价）时不重复覆盖，但活动仍算命中
				if (price.compareTo(item.getSalesPrice()) >= 0) {
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
				fillStackPolicy(override, activity);
				result.getLadderOverrides().add(override);
				// 让利只记「相对替换前生效价」的增量：替换身份价覆盖时原覆盖的让利
				// 已计入其活动明细，按基价全额累计会与会员/出清明细重复计算
				BigDecimal previousPrice = existing != null ? existing.getUnitPrice() : item.getSalesPrice();
				saving = saving.add(previousPrice.subtract(price)
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
	 * 按活动实体填充单价的叠加策略：
	 * 新人专享（type=9）空值回落「不可叠」（防 0 元单），
	 * 会员价（type=10）空值回落「可叠」（身份常态权益），
	 * 其余类型（阶梯价等）回落「可叠」以维持现网行为。
	 */
	private void fillStackPolicy(PromotionCalculationVO.UnitPriceOverride override, PromotionActivity activity) {
		com.aryn.cloud.promotion.api.enums.StackActivityTypeEnum type = null;
		if (PromotionActivity.TYPE_NEWBIE_PRICE.equals(activity.getActivityType())) {
			type = com.aryn.cloud.promotion.api.enums.StackActivityTypeEnum.NEWBIE;
		}
		else if (PromotionActivity.TYPE_MEMBER_PRICE.equals(activity.getActivityType())) {
			type = com.aryn.cloud.promotion.api.enums.StackActivityTypeEnum.MEMBER_PRICE;
		}
		else if (PromotionActivity.TYPE_CLEARANCE.equals(activity.getActivityType())) {
			type = com.aryn.cloud.promotion.api.enums.StackActivityTypeEnum.CLEARANCE;
		}
		boolean couponDefault = type == null || type.isDefaultCouponStackable();
		boolean pointsDefault = type == null || type.isDefaultPointsStackable();
		override.setCouponStackable(com.aryn.cloud.promotion.api.enums.StackActivityTypeEnum.resolve(
				activity.getCouponStackable(), couponDefault));
		override.setPointsStackable(com.aryn.cloud.promotion.api.enums.StackActivityTypeEnum.resolve(
				activity.getPointsStackable(), pointsDefault));
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

	/** 身份价候选：胜出的活动与其固定单价。 */
	private static final class IdentityCandidate {

		private final PromotionActivity activity;

		private final BigDecimal price;

		private IdentityCandidate(PromotionActivity activity, BigDecimal price) {
			this.activity = activity;
			this.price = price;
		}

		private PromotionActivity activity() {
			return activity;
		}

		private BigDecimal price() {
			return price;
		}

	}

	/** 单个身份价活动的规则与 SKU→价格映射。 */
	private static final class PriceOffer {

		private final PromotionActivity activity;

		private final JSONObject rules;

		private final Map<String, BigDecimal> priceBySku;

		private PriceOffer(PromotionActivity activity, JSONObject rules, Map<String, BigDecimal> priceBySku) {
			this.activity = activity;
			this.rules = rules;
			this.priceBySku = priceBySku;
		}

		private PromotionActivity activity() {
			return activity;
		}

		private JSONObject rules() {
			return rules;
		}

		private Map<String, BigDecimal> priceBySku() {
			return priceBySku;
		}

	}

}