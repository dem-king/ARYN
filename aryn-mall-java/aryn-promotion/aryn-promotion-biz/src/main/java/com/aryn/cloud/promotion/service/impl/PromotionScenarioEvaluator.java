package com.aryn.cloud.promotion.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.entity.PricePolicy;
import com.aryn.cloud.promotion.api.entity.PricePolicyItem;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.enums.StackActivityTypeEnum;
import com.aryn.cloud.promotion.api.vo.FirstOrderEligibilityVO;
import com.aryn.cloud.promotion.api.vo.PromotionCalculationVO;
import com.aryn.cloud.user.api.vo.MemberLevelSnapshotVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 正式预览和草稿模拟共用的确定性计算；所有依赖均为已捕获事实，无查询和交易写入。 */
@Slf4j
public final class PromotionScenarioEvaluator {
	private final PromotionFactSnapshot facts;
	public PromotionScenarioEvaluator(PromotionFactSnapshot facts) { this.facts = java.util.Objects.requireNonNull(facts); }

	public PromotionCalculationVO evaluate() {
		PromotionContextDTO context = facts.context();
		if (context == null || context.getTenantId() == null || context.getSkuItems() == null || context.getSkuItems().isEmpty()) { return emptyResult(); }
		List<PromotionActivity> activities = facts.activities();
		Map<String, PromotionActivity> activityById = activities.stream()
			.collect(Collectors.toMap(PromotionActivity::getId, activity -> activity, (left, right) -> left));
		Set<String> excluded = new HashSet<>();
		Map<String, Long> userUsage = new HashMap<>();
		Map<String, Integer> giftUsage = new HashMap<>();
		// 额度剔除重算始终读取同一资格和消耗快照。
		Map<String, MemberLevelSnapshotVO> memberLevelCache = new HashMap<>(facts.memberLevels());
		// 计算阶段仅使用已捕获事实。
		Map<String, Boolean> firstOrderCache = new HashMap<>();
		Map<String, BigDecimal> monthlyUsageCache = new HashMap<>();
		while (true) {
			List<PromotionActivity> candidates = activities.stream()
				.filter(activity -> !excluded.contains(activity.getId()))
				.toList();
			PromotionCalculationVO result = calculate(context, candidates, memberLevelCache, firstOrderCache,
					monthlyUsageCache);
			PromotionActivity exhausted = firstExhausted(context, result, activityById, userUsage, giftUsage,
					monthlyUsageCache);
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
			Map<String, MemberLevelSnapshotVO> memberLevelCache, Map<String, Boolean> firstOrderCache,
			Map<String, BigDecimal> monthlyUsageCache) {
		PromotionCalculationVO result = emptyResult();
		// 协议价（编码 8，price_policy 承载）：最高优先级，命中 SKU 的成交基价被锁定，
		// 阶梯价/身份价/档期特供/新人价一律不再覆盖（合同稳定性优先）。
		Set<String> policyLocked = applyPolicyPrices(context, result);
		List<PromotionActivity> ladderHits = new ArrayList<>();
		List<PromotionActivity> wholeHits = new ArrayList<>();
		List<PromotionActivity> giftHits = new ArrayList<>();
		List<PromotionActivity> memberHits = new ArrayList<>();
		List<PromotionActivity> clearanceHits = new ArrayList<>();
		List<PromotionActivity> specialHits = new ArrayList<>();
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
			else if (PromotionActivity.TYPE_CALENDAR_SPECIAL.equals(activity.getActivityType())) {
				specialHits.add(activity);
			}
			else if (PromotionActivity.TYPE_NEWBIE_PRICE.equals(activity.getActivityType())) {
				newbieHits.add(activity);
			}
		}

		applyBestLadderPrice(context, ladderHits, result, policyLocked);
		// 会员价/出清价/档期特供：在阶梯价之后、新人价之前统一落价，同 SKU 取低。
		// 返回「已被身份价锁定的 SKU → 实际生效价」：新人价与其取低（2026-11-08 语义调整，
		// 原为身份价无条件优先——¥1 新人钩子被全店会员价压制永不生效），同价身份价保留。
		Map<String, BigDecimal> identityPricedPrices = applyBestIdentityPrice(context, memberHits, clearanceHits,
				specialHits, result, memberLevelCache, policyLocked);
		// 新人价必须先于整船优惠落定：整船门槛要排除已享新人价（及会员价/出清价）的 SKU
		// （新人价部分覆盖只排除首件让利部分，超出件按原价计入门槛）
		applyBestNewbiePrice(context, newbieHits, result, new HashMap<>(), identityPricedPrices, policyLocked);
		applyBestWholeDiscount(context, wholeHits, result, firstOrderCache);
		applyBestGift(context, giftHits, result);
		return result;
	}

	private PromotionActivity firstExhausted(PromotionContextDTO context, PromotionCalculationVO result,
			Map<String, PromotionActivity> activityById, Map<String, Long> userUsage, Map<String, Integer> giftUsage,
			Map<String, BigDecimal> monthlyUsageCache) {
		for (PromotionCalculationVO.ActivityDetail detail : result.getDetails()) {
			PromotionActivity activity = activityById.get(detail.getActivityId());
			if (activity == null) {
				continue;
			}
			if (budgetExhausted(activity, detail.getDiscountAmount())) {
				return activity;
			}
			if (monthlyBudgetExhausted(context, activity, detail.getDiscountAmount(), monthlyUsageCache)) {
				return activity;
			}
			if (perUserExhausted(context, activity, result, userUsage)) {
				return activity;
			}
			if (quotaExhausted(context, activity, result)) {
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

	/**
	 * 月度预算上限：规则 {@code rules.monthlyBudget}（元），0/空不限。
	 *
	 * <p>与活动总预算互补：总预算管整个活动周期，月度预算防止单月被一次性打穿。
	 * 统计口径只含 LOCKED/CONFIRMED；取消释放后不占月度额度。
	 */
	private boolean monthlyBudgetExhausted(PromotionContextDTO context, PromotionActivity activity,
			BigDecimal currentDiscount, Map<String, BigDecimal> monthlyUsageCache) {
		BigDecimal current = currentDiscount == null ? BigDecimal.ZERO : currentDiscount;
		if (current.compareTo(BigDecimal.ZERO) <= 0) {
			return false;
		}
		BigDecimal monthlyBudget;
		try {
			monthlyBudget = JSONUtil.parseObj(activity.getRules()).getBigDecimal("monthlyBudget");
		}
		catch (Exception ex) {
			return false;
		}
		if (monthlyBudget == null || monthlyBudget.compareTo(BigDecimal.ZERO) <= 0) {
			return false;
		}
		BigDecimal used = monthlyUsageCache.computeIfAbsent(activity.getId(),
				id -> facts.monthlyUsage().getOrDefault(id, BigDecimal.ZERO));
		boolean exhausted = used.add(current).compareTo(monthlyBudget) > 0;
		if (exhausted) {
			log.warn("活动[{}]月度预算已用尽 used={} budget={}", activity.getId(), used, monthlyBudget);
		}
		else if (used.add(current).compareTo(monthlyBudget.multiply(new BigDecimal("0.8"))) >= 0) {
			log.warn("活动[{}]月度预算已使用80% used={} budget={}", activity.getId(), used, monthlyBudget);
		}
		return exhausted;
	}

	private boolean perUserExhausted(PromotionContextDTO context, PromotionActivity activity,
			PromotionCalculationVO result, Map<String, Long> userUsage) {
		if (activity.getPerUserLimit() == null || activity.getPerUserLimit() <= 0
				|| !StringUtils.hasText(context.getUserId())) {
			return false;
		}
		if (PromotionActivity.TYPE_CALENDAR_SPECIAL.equals(activity.getActivityType())) {
			// 档期特供每人限购按件数（方案 §4.3）：历史占用 + 本单命中件数超上限即剔除
			long usedUnits = userUsage.computeIfAbsent(activity.getId(),
					id -> facts.userUsage().getOrDefault(id, 0L));
			int currentUnits = result.getLadderOverrides().stream()
				.filter(override -> activity.getId().equals(override.getActivityId()))
				.mapToInt(override -> quantityOf(context, override.getSkuId()))
				.sum();
			return usedUnits + currentUnits > activity.getPerUserLimit();
		}
		long used = userUsage.computeIfAbsent(activity.getId(),
				id -> facts.userUsage().getOrDefault(id, 0L));
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
				id -> facts.giftUsage().getOrDefault(id, 0));
		return used + current > activity.getGiftStock();
	}

	/**
	 * 档期特供限量熔断（件数）：{@code total_quota} 为空/0 视为不限。
	 *
	 * <p>已消耗量取 {@code consumed_quota}（reserve 原子累加、release 归还），本单占用
	 * 按本次命中该活动的 SKU 购买量合计；超出则剔除该活动重算，保证展示价=可成交价。
	 */
	private boolean quotaExhausted(PromotionContextDTO context, PromotionActivity activity,
			PromotionCalculationVO result) {
		if (!PromotionActivity.TYPE_CALENDAR_SPECIAL.equals(activity.getActivityType())
				|| activity.getTotalQuota() == null || activity.getTotalQuota() <= 0) {
			return false;
		}
		int current = result.getLadderOverrides().stream()
			.filter(override -> activity.getId().equals(override.getActivityId()))
			.mapToInt(override -> quantityOf(context, override.getSkuId()))
			.sum();
		int used = activity.getConsumedQuota() == null ? 0 : activity.getConsumedQuota();
		return used + current > activity.getTotalQuota();
	}

	private int quantityOf(PromotionContextDTO context, String skuId) {
		return context.getSkuItems().stream()
			.filter(item -> item.getSkuId().equals(skuId))
			.mapToInt(PromotionContextDTO.SkuItem::getQuantity)
			.findFirst()
			.orElse(0);
	}

	/**
	 * 阶梯价：同类型取“总价优惠最大”的活动；规则按 SKU 配档（rules.skuLadders），
	 * 逐 SKU 按数量命中最高档位单价，未配档的 SKU 不改价。
	 *
	 * @param policyLocked 已被协议价锁定的 SKU（一律跳过，协议价不可被公开活动覆盖）
	 */
	private void applyBestLadderPrice(PromotionContextDTO context, List<PromotionActivity> activities,
			PromotionCalculationVO result, Set<String> policyLocked) {
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
						if (!skuId.equals(item.getSkuId()) || policyLocked.contains(item.getSkuId())) {
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
	 *
	 * <p>例外——新人价部分覆盖（首件优惠）：<b>超出件按替换前生效价成交</b>（身份价保留时
	 * 取身份价，否则基价），该部分金额计入门槛基数（与普通商品同权），仅首件让利部分排除，
	 * 避免「小计够门槛却因新人价商品在场而被排除」；与订单侧超出行成交价同口径。
	 */
	private void applyBestWholeDiscount(PromotionContextDTO context, List<PromotionActivity> activities,
			PromotionCalculationVO result, Map<String, Boolean> firstOrderCache) {
		Set<String> activityPricedSkuIds = context.getSkuItems().stream()
			.filter(item -> Boolean.TRUE.equals(item.getActivityPriced()))
			.map(PromotionContextDTO.SkuItem::getSkuId)
			.collect(Collectors.toSet());
		result.getLadderOverrides().forEach(override -> activityPricedSkuIds.add(override.getSkuId()));
		Map<String, PromotionCalculationVO.UnitPriceOverride> partialOverrideBySku = result.getLadderOverrides().stream()
			.filter(override -> override.getDiscountedQuantity() != null)
			.collect(Collectors.toMap(PromotionCalculationVO.UnitPriceOverride::getSkuId,
					override -> override, (first, second) -> first));
		BigDecimal baseAmount = BigDecimal.ZERO;
		int eligibleQuantity = 0;
		for (PromotionContextDTO.SkuItem item : context.getSkuItems()) {
			if (!activityPricedSkuIds.contains(item.getSkuId())) {
				baseAmount = baseAmount
					.add(item.getSalesPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
				eligibleQuantity += item.getQuantity();
				continue;
			}
			// 新人价首件优惠 SKU：仅优惠件数让利不参与门槛，超出件按替换前生效价计入
			PromotionCalculationVO.UnitPriceOverride partial = partialOverrideBySku.get(item.getSkuId());
			if (partial != null && partial.getDiscountedQuantity() < item.getQuantity()) {
				BigDecimal remainderUnitPrice = partial.getRemainderUnitPrice() != null
						? partial.getRemainderUnitPrice() : item.getSalesPrice();
				baseAmount = baseAmount.add(remainderUnitPrice
					.multiply(BigDecimal.valueOf(item.getQuantity() - partial.getDiscountedQuantity())));
				eligibleQuantity += item.getQuantity() - partial.getDiscountedQuantity();
			}
		}

		PromotionActivity best = null;
		BigDecimal bestDiscount = BigDecimal.ZERO;
		JSONObject bestRules = null;
		for (PromotionActivity activity : activities) {
			try {
				if (!firstOrderEligible(activity, context, firstOrderCache)) {
					continue;
				}
				JSONObject rules = JSONUtil.parseObj(activity.getRules());
				JSONArray tiers = rules.getJSONArray("tiers");
				if (tiers == null || tiers.isEmpty()) {
					continue;
				}
				BigDecimal discount = BigDecimal.ZERO;
				for (Object element : tiers) {
					JSONObject tier = (JSONObject) element;
					BigDecimal minAmount = tier.getBigDecimal("minAmount");
					// 件数维度（2026-11-14 B2B）：minQty 按可参与明细件数判定，与金额维度
					// 同档可并存（两者都配置时需同时满足）；历史档位只配 minAmount 不受影响
					Integer minQty = tier.getInt("minQty");
					BigDecimal discountAmount = tier.getBigDecimal("discountAmount");
					if (discountAmount == null || discountAmount.compareTo(BigDecimal.ZERO) <= 0) {
						continue;
					}
					if (minAmount == null && minQty == null) {
						continue;
					}
					boolean amountHit = minAmount == null || baseAmount.compareTo(minAmount) >= 0;
					boolean qtyHit = minQty == null || eligibleQuantity >= minQty;
					if (amountHit && qtyHit && discountAmount.compareTo(discount) > 0) {
						discount = discountAmount;
					}
				}
				// 主体首单单笔封顶：避免大额整船采购一单打穿活动预算
				BigDecimal maxDiscount = rules.getBigDecimal("maxDiscountAmount");
				if (maxDiscount != null && maxDiscount.compareTo(BigDecimal.ZERO) > 0
						&& discount.compareTo(maxDiscount) > 0) {
					discount = maxDiscount;
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
	 * 首单活动资格：只有规则 {@code firstOrderOnly=true} 才校验；
	 * subjectType=1 按船、2 按采购主体。远程异常/缺主体一律不命中（fail-closed）。
	 */
	private boolean firstOrderEligible(PromotionActivity activity, PromotionContextDTO context, Map<String, Boolean> cache) {
		JSONObject rules;
		try { rules = JSONUtil.parseObj(activity.getRules()); }
		catch (Exception invalid) { return false; }
		if (!Boolean.TRUE.equals(rules.getBool("firstOrderOnly", false))) { return true; }
		String type = rules.getStr("subjectType");
		String id = FirstOrderEligibilityVO.SUBJECT_SHIP.equals(type) ? context.getVesselId() : context.getBuyerOrgId();
		return facts.firstOrderEligibility().getOrDefault(activity.getId() + ":" + type + ":" + id, false);
	}


	private Map<String, BigDecimal> giftPrices(List<PromotionActivity> activities) {
		return facts.giftPrices();
	}

	/**
	 * 范围匹配：scope_type 决定匹配键；purchase_scene 非空时额外限定。
	 */
	private boolean matchesScope(PromotionActivity activity, PromotionContextDTO context) {
		return matchesScope(activity, context, facts.frequentSkuIds());
	}

	static boolean matchesScope(PromotionActivity activity, PromotionContextDTO context, Set<String> frequent) {
		if (activity.getScopeType() == null) {
			return false;
		}
		if (StringUtils.hasText(activity.getPurchaseScene())
				&& !activity.getPurchaseScene().equals(context.getPurchaseScene())) {
			return false;
		}
		try {
			JSONObject rules=JSONUtil.parseObj(activity.getRules());
			if(rules.containsKey("campaignTarget")) {
				JSONObject target=rules.getJSONObject("campaignTarget");
				if(target==null || target.isEmpty() || target.keySet().stream().anyMatch(key -> !Set.of("vesselId","buyerOrgId").contains(key))){return false;}
				if(target.containsKey("vesselId") && !Objects.equals(target.getStr("vesselId"),context.getVesselId())){return false;}
				if(target.containsKey("buyerOrgId") && !Objects.equals(target.getStr("buyerOrgId"),context.getBuyerOrgId())){return false;}
			}
		} catch(Exception invalid) {return false;}
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
			case "8" -> context.getSkuItems().stream()
				.anyMatch(item -> frequent.contains(item.getSkuId()) && parseScopeSkus(activity).contains(item.getSkuId()));
			default -> false;
		};
	}

	private static Set<String> parseScopeSkus(PromotionActivity activity) {
		if (!StringUtils.hasText(activity.getScopeValue())) {
			return Set.of();
		}
		return new HashSet<>(Arrays.asList(activity.getScopeValue().split(",")));
	}

	private static boolean containsValue(String scopeValue, String value) {
		if (!StringUtils.hasText(scopeValue) || !StringUtils.hasText(value)) {
			return false;
		}
		return Arrays.asList(scopeValue.split(",")).contains(value);
	}

	/**
	 * 协议价（price_policy）：最高优先级价格层。
	 *
	 * <p>命中逻辑：生效中的政策按 apply_type 匹配上下文（1采购主体 2船舶 3客群标签）；
	 * 政策明细（price_policy_item）对当前单 SKU 逐条解析价格（固定价/折扣率），
	 * 按「priority 取大、同级取更低价」选出唯一胜者。倒挂（协议价 ≥ 当前基价）不落价。
	 *
	 * <p>命中 SKU 的成交基价被<b>锁定</b>：阶梯价/身份价/档期特供/新人价一律跳过，
	 * 且协议价 SKU 不参与整船优惠门槛与分摊（经 ladderOverrides 自动排除）。
	 * 订单级让利（满量优惠/定向券/积分）按方案 §4.1 可叠在协议价基价之上。
	 * 明细起订量（min_qty）未达时协议价不生效，公开价格层继续。
	 *
	 * @return 被协议价锁定的 SKU 集合
	 */
	private Set<String> applyPolicyPrices(PromotionContextDTO context, PromotionCalculationVO result) {
		List<PricePolicy> policies = facts.policies();
		if (policies.isEmpty()) {
			return Set.of();
		}
		Map<String, PricePolicy> policyById = policies.stream()
			.collect(Collectors.toMap(PricePolicy::getId, policy -> policy, (left, right) -> left));
		List<PricePolicyItem> items = facts.policyItems();
		if (items.isEmpty()) {
			return Set.of();
		}
		Map<String, List<PricePolicyItem>> itemsBySku = items.stream()
			.collect(Collectors.groupingBy(PricePolicyItem::getSkuId));
		Map<String, BigDecimal> savingByPolicy = new LinkedHashMap<>();
		Map<String, PricePolicy> participating = new LinkedHashMap<>();
		Set<String> locked = new HashSet<>();
		for (PromotionContextDTO.SkuItem item : context.getSkuItems()) {
			PricePolicyItem bestItem = null;
			PricePolicy bestPolicy = null;
			BigDecimal bestPrice = null;
			for (PricePolicyItem policyItem : itemsBySku.getOrDefault(item.getSkuId(), List.of())) {
				PricePolicy policy = policyById.get(policyItem.getPolicyId());
				if (policy == null) {
					continue;
				}
				if (!PolicyPriceResolver.minQtySatisfied(policyItem, item.getQuantity())) {
					// 未达起订量：合同价不生效，公开价格层继续（C 端展示「N 件起」同口径）
					continue;
				}
				BigDecimal price = PolicyPriceResolver.resolve(policyItem, policy.getPriceMode(), item.getSalesPrice());
				if (price == null || price.compareTo(item.getSalesPrice()) >= 0) {
					// 协议价非法或倒挂（≥ 现售价）不落价，公开价格层继续生效
					continue;
				}
				int policyPriority = policy.getPriority() == null ? 0 : policy.getPriority();
				int bestPriority = bestPolicy == null ? Integer.MIN_VALUE
						: (bestPolicy.getPriority() == null ? 0 : bestPolicy.getPriority());
				if (bestItem == null || policyPriority > bestPriority
						|| (policyPriority == bestPriority && price.compareTo(bestPrice) < 0)) {
					bestItem = policyItem;
					bestPolicy = policy;
					bestPrice = price;
				}
			}
			if (bestItem == null) {
				continue;
			}
			locked.add(item.getSkuId());
			participating.putIfAbsent(bestPolicy.getId(), bestPolicy);
			PromotionCalculationVO.UnitPriceOverride override = new PromotionCalculationVO.UnitPriceOverride();
			override.setSkuId(item.getSkuId());
			override.setUnitPrice(bestPrice);
			override.setOriginalPrice(item.getSalesPrice());
			override.setActivityId(bestPolicy.getId());
			override.setActivityType(PromotionActivity.TYPE_POLICY_PRICE);
			// 订单级让利（满量优惠/定向券/积分）可叠在协议价基价之上（方案 §4.1）；
			// 公开活动价不可覆盖由 policyLocked 锁定保证，与叠加标记无关
			override.setCouponStackable(StackActivityTypeEnum.POLICY.isDefaultCouponStackable());
			override.setPointsStackable(StackActivityTypeEnum.POLICY.isDefaultPointsStackable());
			result.getLadderOverrides().add(override);
			savingByPolicy.merge(bestPolicy.getId(),
				item.getSalesPrice().subtract(bestPrice).multiply(BigDecimal.valueOf(item.getQuantity())),
				BigDecimal::add);
		}
		if (locked.isEmpty()) {
			return Set.of();
		}
		for (Map.Entry<String, PricePolicy> entry : participating.entrySet()) {
			JSONObject snapshot = new JSONObject();
			snapshot.set("policyId", entry.getKey());
			snapshot.set("priceMode", entry.getValue().getPriceMode());
			PromotionCalculationVO.ActivityDetail detail = new PromotionCalculationVO.ActivityDetail();
			detail.setActivityId(entry.getKey());
			detail.setActivityType(PromotionActivity.TYPE_POLICY_PRICE);
			detail.setActivityName(entry.getValue().getPolicyName());
			detail.setDiscountAmount(savingByPolicy.getOrDefault(entry.getKey(), BigDecimal.ZERO));
			detail.setRuleSnapshot(snapshot.toString());
			result.getDetails().add(detail);
			BigDecimal saving = savingByPolicy.getOrDefault(entry.getKey(), BigDecimal.ZERO);
			if (saving.compareTo(BigDecimal.ZERO) > 0) {
				result.setTotalDiscount(result.getTotalDiscount().add(saving));
			}
		}
		return locked;
	}

	/** 生效中的价格政策（status=1 且时间窗覆盖当前），并按上下文过滤适用对象。 */
	

	/** 用户会员标签集合（协议价客群圈定）；远程失败返回空集合（fail-closed，不误放协议价）。 */
	

	/**
	 * 主体企业等级挂载的协议价政策池（与 PricePolicyService 展示口径同源）。
	 * 船舶/企业上下文各查一次权益聚合取并集；失败按空池（fail-closed）。
	 */
	

	

	/** 协议价单条解析已抽取至 {@link PolicyPriceResolver}（引擎/查价/dry-run 三处同源）。 */


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
			List<PromotionActivity> clearanceActivities, List<PromotionActivity> specialActivities,
			PromotionCalculationVO result, Map<String, MemberLevelSnapshotVO> memberLevelCache,
			Set<String> policyLocked) {
		List<PriceOffer> memberOffers = memberOffers(context, memberActivities, memberLevelCache);
		List<PriceOffer> clearanceOffers = clearanceOffers(context, clearanceActivities);
		List<PriceOffer> specialOffers = specialOffers(specialActivities);
		if (memberOffers.isEmpty() && clearanceOffers.isEmpty() && specialOffers.isEmpty()) {
			return Map.of();
		}
		return mergeIdentityOverrides(context, memberOffers, clearanceOffers, specialOffers, result, policyLocked);
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
		LocalDateTime now = facts.asOf();
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
	 * 身份价覆盖合并：逐 SKU 在档期特供/会员/出清活动中取最低价，再与阶梯价比较取更低价，
	 * 逐 SKU 生成一条覆盖，避免改价链（applyPromotionLadder 按 skuId findFirst）出现随机覆盖。
	 *
	 * <p>价格低于基价即落价并计入总让利；阶梯价更低时保留阶梯价（用户更优），
	 * 但该 SKU 仍计入锁定集合供新人价取低比较。倒挂（价格 ≥ 基价）的 SKU
	 * 不锁定，新人价仍可对其生效。每个参与的活动各自产出明细，同 SKU 的让利只归胜出方，
	 * 不会重复计算。档期特供的限量件数（quotaUnits）随明细快照下发，reserve 据此原子扣减。
	 *
	 * @return 已被身份价锁定的 SKU → 实际生效价（阶梯价保留时为阶梯价），供新人价取低比较
	 */
	private Map<String, BigDecimal> mergeIdentityOverrides(PromotionContextDTO context, List<PriceOffer> memberOffers,
			List<PriceOffer> clearanceOffers, List<PriceOffer> specialOffers, PromotionCalculationVO result,
			Set<String> policyLocked) {
		Map<String, BigDecimal> lockedPrices = new LinkedHashMap<>();
		Map<String, BigDecimal> savingByActivity = new LinkedHashMap<>();
		Map<String, PromotionActivity> participating = new LinkedHashMap<>();
		Map<String, Integer> specialUnitsByActivity = new LinkedHashMap<>();
		for (PromotionContextDTO.SkuItem item : context.getSkuItems()) {
			if (policyLocked.contains(item.getSkuId())) {
				continue;
			}
			IdentityCandidate winner = chooseIdentityPrice(specialOffers, memberOffers, clearanceOffers, item);
			if (winner == null) {
				continue;
			}
			PromotionCalculationVO.UnitPriceOverride existing = findOverride(result, item.getSkuId());
			if (existing != null && existing.getUnitPrice().compareTo(winner.price()) <= 0) {
				// 阶梯价更低：保留阶梯价（用户更优），锁定价记阶梯价——新人价与实际生效价取低。
				// 特供未实际落价则不占限量名额（2026-11-15 审查修复：此前先计数后比价导致多扣）
				lockedPrices.put(item.getSkuId(), existing.getUnitPrice());
				continue;
			}
			// 只有实际落价的活动才计入参与明细与特供限量占用
			participating.putIfAbsent(winner.activity().getId(), winner.activity());
			if (PromotionActivity.TYPE_CALENDAR_SPECIAL.equals(winner.activity().getActivityType())) {
				specialUnitsByActivity.merge(winner.activity().getId(), item.getQuantity(), Integer::sum);
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
		appendIdentityDetails(specialOffers, participating, savingByActivity, result, specialUnitsByActivity);
		appendIdentityDetails(memberOffers, participating, savingByActivity, result, Map.of());
		appendIdentityDetails(clearanceOffers, participating, savingByActivity, result, Map.of());
		return lockedPrices;
	}

	/**
	 * 身份价同 SKU 取低：档期特供（stackPriority=8）> 会员价 > 出清价，
	 * 同价按该顺序保留（档期特供限量运营归属优先，会员价身份权益次之）。
	 */
	private IdentityCandidate chooseIdentityPrice(List<PriceOffer> specialOffers, List<PriceOffer> memberOffers,
			List<PriceOffer> clearanceOffers, PromotionContextDTO.SkuItem item) {
		IdentityCandidate special = bestOfferCandidate(specialOffers, item);
		IdentityCandidate member = bestMemberCandidate(memberOffers, item);
		IdentityCandidate clearance = bestOfferCandidate(clearanceOffers, item);
		// 三候选可空，逐个取低（特供 > 会员 > 出清 的同价优先级由首个非空者保序）
		IdentityCandidate best = null;
		for (IdentityCandidate candidate : new IdentityCandidate[] {special, member, clearance}) {
			if (candidate == null) {
				continue;
			}
			if (best == null || candidate.price().compareTo(best.price()) < 0) {
				best = candidate;
			}
		}
		return best;
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
			Map<String, BigDecimal> savingByActivity, PromotionCalculationVO result,
			Map<String, Integer> specialUnitsByActivity) {
		for (PriceOffer offer : offers) {
			if (!participating.containsKey(offer.activity().getId())) {
				continue;
			}
			BigDecimal saving = savingByActivity.getOrDefault(offer.activity().getId(), BigDecimal.ZERO);
			JSONObject snapshotRules = offer.rules();
			// 档期特供把本单占用的限量件数写进快照：reserve 原子扣减、release 归还都以此为准
			Integer quotaUnits = specialUnitsByActivity.get(offer.activity().getId());
			if (quotaUnits != null) {
				snapshotRules = JSONUtil.parseObj(JSONUtil.toJsonStr(snapshotRules));
				snapshotRules.set("quotaUnits", quotaUnits);
			}
			PromotionCalculationVO.ActivityDetail detail = new PromotionCalculationVO.ActivityDetail();
			detail.setActivityId(offer.activity().getId());
			detail.setActivityType(offer.activity().getActivityType());
			detail.setActivityName(offer.activity().getActivityName());
			detail.setDiscountAmount(saving);
			detail.setRuleSnapshot(JSONUtil.toJsonStr(snapshotRules));
			result.getDetails().add(detail);
			if (saving.compareTo(BigDecimal.ZERO) > 0) {
				result.setTotalDiscount(result.getTotalDiscount().add(saving));
			}
		}
	}

	/** 档期特供价（type=12）：rules.skus 同出清价形态 [{skuId,price}]，限量/限购由额度剔除与 reserve 兜底。 */
	private List<PriceOffer> specialOffers(List<PromotionActivity> activities) {
		if (activities.isEmpty()) {
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
				Map<String, BigDecimal> priceBySku = parsePriceBySku(skus, "price");
				if (!priceBySku.isEmpty()) {
					offers.add(new PriceOffer(activity, rules, priceBySku));
				}
			}
			catch (Exception ex) {
				log.warn("档期特供活动[{}]规则解析失败，跳过: {}", activity.getId(), ex.getMessage());
			}
		}
		return offers;
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
	private MemberLevelSnapshotVO userLevel(String tenantId, String userId, Map<String, MemberLevelSnapshotVO> cache) {
		return cache.get("user:" + userId);
	}

	/** 活动门槛等级快照（本次 preview 缓存，含 null；异常按无门槛缓存，fail-closed）。 */
	private MemberLevelSnapshotVO level(String tenantId, String levelId, Map<String, MemberLevelSnapshotVO> cache) {
		return cache.get("level:" + levelId);
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
			Map<String, BigDecimal> identityPricedPrices, Set<String> policyLocked) {
		if (activities.isEmpty() || !StringUtils.hasText(context.getUserId())) {
			return;
		}
		PromotionActivity best = null;
		JSONObject bestRules = null;
		BigDecimal bestSaving = BigDecimal.ZERO;
		int bestLimitPerUser = 1;
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
				int limitPerUser = normalizeNewbieLimitPerUser(rules);
				BigDecimal saving = newbieSaving(context, newbieSkus, identityPricedPrices, limitPerUser, policyLocked);
				// 资格通过即视为命中（saving 为 0 表示本次基价已等于活动价，即重放场景）；
				// 多活动并存时仍取节省额最大者，节省额相同时取先出现的（稳定）
				if (best == null || saving.compareTo(bestSaving) > 0) {
					bestSaving = saving;
					best = activity;
					bestRules = rules;
					bestLimitPerUser = limitPerUser;
				}
			}
			catch (Exception ex) {
				log.warn("新人专享活动[{}]规则解析失败，跳过: {}", activity.getId(), ex.getMessage());
			}
		}
		if (best != null) {
			mergeNewbieOverrides(context, best, bestRules, result, identityPricedPrices, bestLimitPerUser, policyLocked);
		}
	}

	/**
	 * 每人限购件数：rules.limitPerUser 缺省/非法时按 1 件处理。
	 *
	 * <p>语义为「每人每 SKU 享受新人价的件数」：首 N 件按新人价成交，超出件按原价
	 * 正常下单（不再整单拦截）。发布校验当前固定 1，此处归一保证规则宽松时也不放大让利。
	 */
	private int normalizeNewbieLimitPerUser(JSONObject rules) {
		Integer limit = rules.getInt("limitPerUser");
		return limit == null || limit <= 0 ? 1 : limit;
	}

	/**
	 * 活动命中 SKU 相对当前生效价的总节省额（仅统计真正降价的 SKU）。
	 *
	 * <p>被身份价锁定的 SKU 以「min(基价, 身份价)」为比较基准：新人价低于它才计节省
	 * （该 SKU 的新人价合并时才会真正生效），与 {@link #mergeNewbieOverrides} 的取低口径一致。
	 *
	 * <p>节省额按<b>优惠件数</b>（每人限购数与购买量的较小值）计：超出件按原价成交，
	 * 不产生让利，与订单侧首件让利口径一致。
	 */
	private BigDecimal newbieSaving(PromotionContextDTO context, JSONArray newbieSkus,
			Map<String, BigDecimal> identityPricedPrices, int limitPerUser, Set<String> policyLocked) {
		BigDecimal saving = BigDecimal.ZERO;
		for (Object element : newbieSkus) {
			JSONObject sku = (JSONObject) element;
			String skuId = sku.getStr("skuId");
			BigDecimal price = sku.getBigDecimal("price");
			if (!StringUtils.hasText(skuId) || price == null || price.compareTo(BigDecimal.ZERO) < 0) {
				continue;
			}
			for (PromotionContextDTO.SkuItem item : context.getSkuItems()) {
				if (!skuId.equals(item.getSkuId()) || policyLocked.contains(item.getSkuId())) {
					continue;
				}
				BigDecimal identityPrice = identityPricedPrices.get(skuId);
				BigDecimal effectiveBase = identityPrice != null
						&& identityPrice.compareTo(item.getSalesPrice()) < 0
								? identityPrice
								: item.getSalesPrice();
				if (price.compareTo(effectiveBase) < 0) {
					int discountedQuantity = Math.min(limitPerUser, item.getQuantity());
					saving = saving.add(effectiveBase.subtract(price)
							.multiply(BigDecimal.valueOf(discountedQuantity)));
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
	 * <p><b>优惠件数</b>：override 带 {@code discountedQuantity = min(limitPerUser, 购买量)}，
	 * 首件按新人价成交、超出件由订单侧按原价拆行成单；节省额/明细让利同样只按优惠件数计。
	 *
	 * @param limitPerUser 每人每 SKU 享受新人价的件数（规则缺省按 1）
	 * @return 是否命中（命中=活动对本单至少一个 SKU 的实际生效价做出了贡献）
	 */
	private boolean mergeNewbieOverrides(PromotionContextDTO context, PromotionActivity activity, JSONObject rules,
			PromotionCalculationVO result, Map<String, BigDecimal> identityPricedPrices, int limitPerUser,
			Set<String> policyLocked) {
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
				if (!skuId.equals(item.getSkuId()) || policyLocked.contains(item.getSkuId())) {
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
				override.setDiscountedQuantity(Math.min(limitPerUser, item.getQuantity()));
				// 超出件保留替换前生效价：身份价（会员/出清）被取低替换时不剥夺常态权益，
				// 无身份价时即基价；订单侧超出行与整船门槛都按此价成交/累计
				override.setRemainderUnitPrice(existing != null ? existing.getUnitPrice() : item.getSalesPrice());
				override.setActivityId(activity.getId());
				override.setActivityType(activity.getActivityType());
				fillStackPolicy(override, activity);
				result.getLadderOverrides().add(override);
				// 让利只记「相对替换前生效价」的增量：替换身份价覆盖时原覆盖的让利
				// 已计入其活动明细，按基价全额累计会与会员/出清明细重复计算；
				// 且只按优惠件数计——超出件按原价成交，不产生让利
				BigDecimal previousPrice = existing != null ? existing.getUnitPrice() : item.getSalesPrice();
				saving = saving.add(previousPrice.subtract(price)
						.multiply(BigDecimal.valueOf(Math.min(limitPerUser, item.getQuantity()))));
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
	 * 新人专享（type=9）与档期特供（type=12）空值回落「不可叠」（防 0 元单/限量硬促销），
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
		else if (PromotionActivity.TYPE_CALENDAR_SPECIAL.equals(activity.getActivityType())) {
			type = com.aryn.cloud.promotion.api.enums.StackActivityTypeEnum.CALENDAR_SPECIAL;
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
	private boolean eligibleAsNewbie(PromotionContextDTO context, Integer registerWithinDays, Map<Integer, Boolean> eligibilityCache) {
		return facts.newbieEligibility().getOrDefault(registerWithinDays, false);
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