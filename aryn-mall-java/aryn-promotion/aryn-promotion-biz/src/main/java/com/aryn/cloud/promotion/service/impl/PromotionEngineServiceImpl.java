package com.aryn.cloud.promotion.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.entity.PricePolicy;
import com.aryn.cloud.promotion.api.entity.PricePolicyItem;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.entity.PromoCalendar;
import com.aryn.cloud.promotion.api.vo.FirstOrderEligibilityVO;
import com.aryn.cloud.promotion.api.vo.PromotionCalculationVO;
import com.aryn.cloud.promotion.mapper.PricePolicyItemMapper;
import com.aryn.cloud.promotion.mapper.PricePolicyMapper;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.mapper.PromoCalendarMapper;
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
import java.util.Objects;
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

	private final PricePolicyMapper pricePolicyMapper;

	private final PricePolicyItemMapper pricePolicyItemMapper;

	private final PromoCalendarMapper promoCalendarMapper;

	private final PromotionLockUsage promotionLockUsage;

	@DubboReference
	private com.aryn.cloud.promotion.api.remote.RemoteBuyerProfileService remoteBuyerProfileService;

	/**
	 * 企业会员远程服务（user 域）：协议价 apply_type=3 的客群标签圈定需要用户标签集合。
	 * 必须用<b>非 final 字段</b>+@DubboReference（构造器注入远程接口曾在 boot 启动失败），
	 * 与 remoteBuyerProfileService 同款写法。查询失败按无标签处理（fail-closed，不误放协议价）。
	 */
	@DubboReference
	private com.aryn.cloud.user.api.remote.RemoteEnterpriseUserService remoteEnterpriseUserService;

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


	@Override
	public PromotionCalculationVO preview(PromotionContextDTO context) {
		return new PromotionScenarioEvaluator(captureFacts(context)).evaluate();
	}

	/** 只读取已发布事实；结果可反复重放，重放阶段不再访问数据库/RPC。 */
	public PromotionFactSnapshot captureFacts(PromotionContextDTO source) {
		return captureFacts(source, List.of(), Map.of());
	}

	/** 草稿仅在当前值快照中覆盖；不改变活动发布态或交易计数器。 */
	public ScenarioFacts captureScenario(PromotionContextDTO context, PromotionActivity draft,
			PromotionActivity replacement) {
		PromotionActivity overlay = new PromotionActivity();
		org.springframework.beans.BeanUtils.copyProperties(draft, overlay);
		Map<String, String> usageIds = Map.of();
		if (replacement != null) {
			overlay.setConsumedBudget(replacement.getConsumedBudget());
			overlay.setConsumedQuota(replacement.getConsumedQuota());
			usageIds = Map.of(overlay.getId(), replacement.getId());
		}
		PromotionFactSnapshot combined = captureFacts(context, List.of(overlay), usageIds);
		List<PromotionActivity> baseline = combined.activities().stream()
			.filter(activity -> !activity.getId().equals(draft.getId())).toList();
		List<PromotionActivity> proposed = combined.activities().stream()
			.filter(activity -> replacement == null || !activity.getId().equals(replacement.getId())).toList();
		return new ScenarioFacts(combined.toBuilder().activities(baseline).build(),
			combined.toBuilder().activities(proposed).build());
	}

	/** 显式协议草稿只覆盖值快照；调用方已验证样本成员及政策归属，不改变正式查询。 */
	public ScenarioFacts capturePolicyScenario(PromotionContextDTO context, PricePolicy policy, List<PricePolicyItem> items) {
		var baseline = captureFacts(context, List.of(), Map.of());
		if (policy == null || !context.getTenantId().equals(policy.getTenantId())
				|| !(PricePolicy.APPLY_VESSEL.equals(policy.getApplyType()) && Objects.equals(policy.getApplyValue(), context.getVesselId())
					|| PricePolicy.APPLY_BUYER_ORG.equals(policy.getApplyType()) && Objects.equals(policy.getApplyValue(), context.getBuyerOrgId()))) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("协议草稿主体与授权样本不一致");
		}
		var policies = new ArrayList<>(baseline.policies()); var proposedItems = new ArrayList<>(baseline.policyItems());
		if (!baseline.asOf().isBefore(policy.getStartTime()) && !baseline.asOf().isAfter(policy.getEndTime())) {
			policies.add(policy); proposedItems.addAll(items);
		}
		return new ScenarioFacts(baseline, baseline.toBuilder().policies(policies).policyItems(proposedItems).build());
	}

	public record ScenarioFacts(PromotionFactSnapshot baseline, PromotionFactSnapshot proposed) { }

	private PromotionFactSnapshot captureFacts(PromotionContextDTO source, List<PromotionActivity> overlays,
			Map<String, String> usageIds) {
		LocalDateTime time = source != null && source.getActivityTime() != null ? source.getActivityTime() : LocalDateTime.now();
		if (source == null || source.getTenantId() == null || source.getSkuItems() == null || source.getSkuItems().isEmpty()) {
			return PromotionFactSnapshot.builder().asOf(time).context(source).build();
		}
		PromotionContextDTO context = PromotionFactSnapshot.copyContext(source);
		context.setActivityTime(time);
		LocalDateTime now = context.getActivityTime();
		List<PromotionActivity> activities = promotionActivityMapper.selectList(
				Wrappers.lambdaQuery(PromotionActivity.class)
						.eq(PromotionActivity::getTenantId, context.getTenantId())
						.eq(PromotionActivity::getStatus, PromotionActivity.STATUS_PUBLISHED)
						.le(PromotionActivity::getStartTime, now)
						.ge(PromotionActivity::getEndTime, now)
						.in(PromotionActivity::getActivityType,
								List.of(PromotionActivity.TYPE_LADDER_PRICE, PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT,
										PromotionActivity.TYPE_GIFT, PromotionActivity.TYPE_NEWBIE_PRICE,
										PromotionActivity.TYPE_MEMBER_PRICE, PromotionActivity.TYPE_CLEARANCE,
										PromotionActivity.TYPE_CALENDAR_SPECIAL)));
		activities = new ArrayList<>(activities);
		for (PromotionActivity overlay : overlays) {
			if (context.getTenantId().equals(overlay.getTenantId()) && overlay.getStartTime() != null
					&& overlay.getEndTime() != null && !now.isBefore(overlay.getStartTime())
					&& !now.isAfter(overlay.getEndTime())) {
				activities.add(overlay);
			}
		}
		// 档期过滤：归属采购档期的活动仅在档期生效窗口内参与（草稿/停用/窗口外一律剔除）
		activities = filterByCalendar(context.getTenantId(), activities, now);
		
		List<PricePolicy> policies = loadEffectivePolicies(context);
		List<PricePolicyItem> items = policies.isEmpty() ? List.of() : pricePolicyItemMapper.selectList(Wrappers.lambdaQuery(PricePolicyItem.class)
			.eq(PricePolicyItem::getTenantId, context.getTenantId()).in(PricePolicyItem::getPolicyId, policies.stream().map(PricePolicy::getId).toList()));
		Map<String, MemberLevelSnapshotVO> levels = new HashMap<>();
		Map<String, Boolean> firstOrders = new HashMap<>();
		Map<Integer, Boolean> newbies = new HashMap<>();
		Map<String, BigDecimal> monthly = new HashMap<>();
		Map<String, Long> userUsage = new HashMap<>();
		Map<String, Integer> giftUsage = new HashMap<>();
		Set<String> frequent = new HashSet<>();
		if (activities.stream().anyMatch(activity -> "8".equals(activity.getScopeType()))) {
			try { frequent.addAll(remoteBuyerProfileService.frequentSkuIds(context.getTenantId(), context.getUserId())); }
			catch (Exception ex) { log.warn("常购画像读取失败，按空事实捕获", ex); }
		}
		activities = activities.stream()
			.filter(activity -> PromotionScenarioEvaluator.matchesScope(activity, context, frequent))
			.toList();
		for (PromotionActivity activity : activities) {
			String usageId = usageIds.getOrDefault(activity.getId(), activity.getId());
			JSONObject rules;
			try {
				rules = JSONUtil.parseObj(activity.getRules());
			}
			catch (Exception invalid) {
				log.warn("活动[{}]规则无法解析，跳过资格事实", activity.getId());
				continue;
			}
			try {
				if (PromotionActivity.TYPE_MEMBER_PRICE.equals(activity.getActivityType()) && StringUtils.hasText(context.getUserId())
						&& rules.getJSONArray("skus") != null && !rules.getJSONArray("skus").isEmpty()
						&& StringUtils.hasText(rules.getStr("minLevelId"))) {
					MemberLevelSnapshotVO user = userLevel(context.getTenantId(), context.getUserId(), levels);
					if (user != null && user.getSortOrder() != null) {
						level(context.getTenantId(), rules.getStr("minLevelId"), levels);
					}
				}
				if (PromotionActivity.TYPE_NEWBIE_PRICE.equals(activity.getActivityType()) && StringUtils.hasText(context.getUserId())) {
					eligibleAsNewbie(context, rules.getInt("registerWithinDays"), newbies);
				}
				if (PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT.equals(activity.getActivityType())) {
					firstOrderEligible(activity, context, firstOrders);
				}
			}
			catch (Exception invalid) {
				log.warn("活动[{}]资格规则异常，按无资格处理", activity.getId());
			}
			// 与正式引擎原口径一致：无效的月度额度配置不阻断其他有效规则。
			BigDecimal monthLimit = null;
			try {
				monthLimit = rules.getBigDecimal("monthlyBudget");
			}
			catch (Exception invalid) {
				log.warn("活动[{}]月度额度无法解析", activity.getId());
			}
			if (monthLimit != null && monthLimit.signum() > 0) {
				monthly.put(activity.getId(), promotionLockUsage.sumDiscountSince(context.getTenantId(), usageId,
					now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0)));
			}
			if (activity.getPerUserLimit() != null && activity.getPerUserLimit() > 0 && StringUtils.hasText(context.getUserId())) {
				userUsage.put(activity.getId(), PromotionActivity.TYPE_CALENDAR_SPECIAL.equals(activity.getActivityType())
					? (long) promotionLockUsage.sumUserQuotaUnits(context.getTenantId(), context.getUserId(), usageId)
					: promotionLockUsage.countUserLocks(context.getTenantId(), context.getUserId(), usageId));
			}
			if (PromotionActivity.TYPE_GIFT.equals(activity.getActivityType()) && activity.getGiftStock() != null && activity.getGiftStock() > 0) {
				giftUsage.put(activity.getId(), promotionLockUsage.sumGiftQuantity(context.getTenantId(), usageId));
			}
		}
		return PromotionFactSnapshot.builder().context(context).asOf(now).activities(activities).policies(policies).policyItems(items)
			.giftPrices(giftPrices(activities.stream()
				.filter(activity -> PromotionActivity.TYPE_GIFT.equals(activity.getActivityType())).toList())).frequentSkuIds(frequent).memberLevels(levels).firstOrderEligibility(firstOrders)
			.newbieEligibility(newbies).monthlyUsage(monthly).userUsage(userUsage).giftUsage(giftUsage).build();
	}


	private boolean firstOrderEligible(PromotionActivity activity, PromotionContextDTO context,
			Map<String, Boolean> cache) {
		JSONObject rules;
		try {
			rules = JSONUtil.parseObj(activity.getRules());
		}
		catch (Exception ex) {
			return false;
		}
		if (!Boolean.TRUE.equals(rules.getBool("firstOrderOnly", false))) {
			return true;
		}
		String subjectType = rules.getStr("subjectType");
		if (!FirstOrderEligibilityVO.SUBJECT_SHIP.equals(subjectType)
				&& !FirstOrderEligibilityVO.SUBJECT_BUYER_ORG.equals(subjectType)) {
			log.warn("首单活动[{}]未配置合法 subjectType，按不命中处理", activity.getId());
			return false;
		}
		String subjectId = FirstOrderEligibilityVO.SUBJECT_SHIP.equals(subjectType)
				? context.getVesselId() : context.getBuyerOrgId();
		if (!StringUtils.hasText(subjectId)) {
			return false;
		}
		String cacheKey = activity.getId() + ":" + subjectType + ":" + subjectId;
		return cache.computeIfAbsent(cacheKey, key -> {
			try {
				FirstOrderEligibilityVO eligibility = FirstOrderEligibilityVO.SUBJECT_SHIP.equals(subjectType)
						? remoteBuyerProfileService.checkShipFirstOrderEligibility(context.getTenantId(), subjectId)
						: remoteBuyerProfileService.checkBuyerOrgFirstOrderEligibility(context.getTenantId(), subjectId);
				return eligibility != null && Boolean.TRUE.equals(eligibility.getEligible());
			}
			catch (Exception ex) {
				log.warn("首单资格查询失败，活动[{}]按不命中处理 subject={}:{}", activity.getId(), subjectType,
						subjectId, ex);
				return false;
			}
		});
	}

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

	private boolean containsValue(String scopeValue, String value) {
		if (!StringUtils.hasText(scopeValue) || !StringUtils.hasText(value)) {
			return false;
		}
		return Arrays.asList(scopeValue.split(",")).contains(value);
	}

	private List<PromotionActivity> filterByCalendar(String tenantId, List<PromotionActivity> activities,
			LocalDateTime now) {
		List<String> calendarIds = activities.stream()
			.map(PromotionActivity::getCalendarId)
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
		if (calendarIds.isEmpty()) {
			return activities;
		}
		Set<String> effectiveIds = promoCalendarMapper.selectList(Wrappers.lambdaQuery(PromoCalendar.class)
				.eq(PromoCalendar::getTenantId, tenantId)
				.eq(PromoCalendar::getStatus, PromoCalendar.STATUS_ACTIVE)
				.le(PromoCalendar::getStartTime, now)
				.ge(PromoCalendar::getEndTime, now)
				.in(PromoCalendar::getId, calendarIds))
			.stream()
			.map(PromoCalendar::getId)
			.collect(Collectors.toSet());
		return activities.stream()
			.filter(activity -> !StringUtils.hasText(activity.getCalendarId())
					|| effectiveIds.contains(activity.getCalendarId()))
			.toList();
	}

	private List<PricePolicy> loadEffectivePolicies(PromotionContextDTO context) {
		LocalDateTime now = context.getActivityTime() != null ? context.getActivityTime() : LocalDateTime.now();
		List<PricePolicy> policies = pricePolicyMapper.selectList(Wrappers.lambdaQuery(PricePolicy.class)
				.eq(PricePolicy::getTenantId, context.getTenantId())
				.eq(PricePolicy::getStatus, PricePolicy.STATUS_ACTIVE)
				.le(PricePolicy::getStartTime, now)
				.ge(PricePolicy::getEndTime, now));
		if (policies.isEmpty()) {
			return List.of();
		}
		// 企业权益「协议价政策池」（benefit_type=3）：等级挂载的政策自动套用（方案 §5.2）
		Set<String> poolIds = policyPoolIds(context);
		// 客群标签一次 preview 只取一次（首个 apply_type=3 政策触发）；失败按无标签（fail-closed）
		Set<String> userTagIds = null;
		List<PricePolicy> matched = new ArrayList<>(policies.size());
		for (PricePolicy policy : policies) {
			if (poolIds.contains(policy.getId())) {
				matched.add(policy);
				continue;
			}
			String applyType = policy.getApplyType();
			if (PricePolicy.APPLY_BUYER_ORG.equals(applyType)) {
				if (containsValue(policy.getApplyValue(), context.getBuyerOrgId())) {
					matched.add(policy);
				}
			}
			else if (PricePolicy.APPLY_VESSEL.equals(applyType)) {
				if (containsValue(policy.getApplyValue(), context.getVesselId())) {
					matched.add(policy);
				}
			}
			else if (PricePolicy.APPLY_TAG.equals(applyType)) {
				if (userTagIds == null) {
					userTagIds = fetchUserTagIds(context);
				}
				if (StringUtils.hasText(policy.getApplyValue()) && !userTagIds.isEmpty()
						&& Arrays.asList(policy.getApplyValue().split(",")).stream().anyMatch(userTagIds::contains)) {
					matched.add(policy);
				}
			}
		}
		return matched;
	}

	private Set<String> fetchUserTagIds(PromotionContextDTO context) {
		if (!StringUtils.hasText(context.getUserId())) {
			return Set.of();
		}
		try {
			List<String> tagIds = remoteEnterpriseUserService.getUserTagIds(context.getTenantId(), context.getUserId());
			return tagIds == null ? Set.of() : Set.copyOf(tagIds);
		}
		catch (Exception ex) {
			log.warn("协议价客群标签查询失败，按无标签处理 tenant={} user={}: {}",
					context.getTenantId(), context.getUserId(), ex.getMessage());
			return Set.of();
		}
	}

	private Set<String> policyPoolIds(PromotionContextDTO context) {
		Set<String> poolIds = new HashSet<>();
		if (StringUtils.hasText(context.getVesselId())) {
			collectPoolIds(poolIds, context.getTenantId(),
					com.aryn.cloud.user.api.constant.EnterpriseMemberConstants.SUBJECT_VESSEL, context.getVesselId());
		}
		if (StringUtils.hasText(context.getBuyerOrgId())) {
			collectPoolIds(poolIds, context.getTenantId(),
					com.aryn.cloud.user.api.constant.EnterpriseMemberConstants.SUBJECT_BUYER_ORG,
					context.getBuyerOrgId());
		}
		return poolIds;
	}

	private void collectPoolIds(Set<String> poolIds, String tenantId, String subjectType, String subjectId) {
		try {
			com.aryn.cloud.user.api.vo.EnterpriseBenefitsVO benefits = remoteEnterpriseUserService
				.getSubjectBenefits(tenantId, subjectType, subjectId);
			if (benefits != null && benefits.getPolicyPoolIds() != null) {
				poolIds.addAll(benefits.getPolicyPoolIds());
			}
		}
		catch (Exception ex) {
			log.warn("协议价政策池查询失败，按空池处理 subject={}:{}", subjectType, subjectId, ex);
		}
	}

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
