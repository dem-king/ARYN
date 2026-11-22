package com.aryn.cloud.promotion.service.impl;

import cn.hutool.core.util.IdUtil;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.remote.RemoteOrderScenarioService;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.dto.CampaignSimulationDTO;
import com.aryn.cloud.promotion.api.entity.CampaignSimulationRecord;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.vo.CampaignSimulationVO;
import com.aryn.cloud.promotion.mapper.CampaignSimulationMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** 保存值快照的模拟凭据。外部事实读取不包在数据库事务内，只写campaign_simulation。 */
@Service
@RequiredArgsConstructor
public class CampaignSimulationService {
	private final CampaignSimulationMapper mapper;
	private final B2bActivityCommandService commands;
	private final ActivityPublishGovernanceService governance;
	private final PromotionEngineServiceImpl engine;
	private final ObjectMapper objectMapper;
	private final CampaignDraftStore campaignDrafts;
	private final PricePolicyService policies;

	@DubboReference private RemoteOrderScenarioService remoteOrderScenarioService;
	@DubboReference private RemoteGoodsSkuService remoteGoodsSkuService;

	public CampaignSimulationVO simulate(String actorId, CampaignSimulationDTO request) {
		requireActor(actorId);
		Calculation calculation = calculate(actorId, request);
		// 长时间RPC后再验源修订，不能把旧规则的结果挂到已变更草稿上。
		if (!calculation.draftDigest().equals(sourceDigest(actorId, calculation.request()))) {
			throw new ArynBusinessException(409, "草稿已变更，请重新模拟");
		}
		CampaignSimulationVO result = calculation.result();
		result.setId(IdUtil.getSnowflakeNextIdStr());
		result.setExpiresAt(LocalDateTime.now().plusMinutes(10).withNano(0));
		result.setCurrent(true);
		CampaignSimulationRecord record = new CampaignSimulationRecord();
		record.setId(result.getId());
		record.setTenantId(tenant());
		record.setActorId(actorId);
		record.setDraftActivityId(result.getDraftActivityId());
		record.setDraftRevision(result.getDraftRevision());
		record.setRequestJson(json(calculation.request()));
		record.setDraftDigest(calculation.draftDigest());
		record.setInputDigest(result.getInputDigest());
		record.setFactDigest(result.getFactDigest());
		record.setResultJson(json(result));
		record.setExpiresAt(result.getExpiresAt());
		record.setCreateTime(LocalDateTime.now());
		record.setDelFlag("0");
		mapper.insert(record);
		if (StringUtils.hasText(result.getCampaignDraftId())) {
			campaignDrafts.bindSimulation(actorId, result.getCampaignDraftId(), result.getExpectedCampaignRevision(), result.getId());
		}
		return result;
	}

	/** 读取与核验均限制当前租户及操作者；重验只读，不续期或创建新凭据。 */
	public CampaignSimulationVO get(String actorId, String id, boolean revalidate) {
		CampaignSimulationRecord record = owned(actorId, id);
		CampaignSimulationVO result = read(record.getResultJson(), CampaignSimulationVO.class);
		if (!record.getExpiresAt().isAfter(LocalDateTime.now())) { return stale(result, "模拟已过期"); }
		CampaignSimulationDTO source = read(record.getRequestJson(), CampaignSimulationDTO.class);
		try {
			if (!record.getDraftDigest().equals(sourceDigest(actorId, source))) { return stale(result, "活动包或主活动修订、状态已变化"); }
		}
		catch (RuntimeException missing) { return stale(result, "源草稿已失效"); }
		if (revalidate) {
			try {
				Calculation current = calculate(actorId, source);
				if (!record.getFactDigest().equals(current.result().getFactDigest())) {
					return stale(result, "商品、权限、活动、权益或额度事实已变化");
				}
			}
			catch (RuntimeException unavailable) { return stale(result, "事实无法重新核验，请重新模拟"); }
		}
		return result;
	}

	/** 发布入口必须重验，不可把缓存展示态current当作发布许可。 */
	public CampaignSimulationVO requireCurrent(String actorId, String id) {
		CampaignSimulationVO result = get(actorId, id, true);
		if (!result.isCurrent()) { throw new ArynBusinessException(409, result.getStaleReason()); }
		return result;
	}

	private Calculation calculate(String actorId, CampaignSimulationDTO input) {
		if (input == null || input.getSample() == null || (input.getExpectedRevision() == null && input.getExpectedCampaignRevision() == null)) {
			throw new ArynBusinessException("草稿修订和场景样本不能为空");
		}
		CampaignSimulationDTO request = read(json(input), CampaignSimulationDTO.class);
		com.aryn.cloud.promotion.api.dto.CampaignDraftSaveDTO definition = null;
		com.aryn.cloud.promotion.api.entity.PricePolicy policy = null;
		List<com.aryn.cloud.promotion.api.entity.PricePolicyItem> policyItems = List.of();
		if (StringUtils.hasText(request.getCampaignDraftId())) {
			var campaign = campaignDrafts.owned(actorId, request.getCampaignDraftId(), false);
			if (!Objects.equals(request.getExpectedCampaignRevision(), campaign.getRevision()) || !"READY".equals(campaign.getStatus())) {
				throw new ArynBusinessException(409, "请选择当前修订的完整活动包草稿");
			}
			definition = campaignDrafts.decode(campaign.getDefinitionJson());
			validateSample(definition, request);
			request.setDraftActivityId(campaign.getMainActivityId()); request.setExpectedRevision(campaign.getMainActivityRevision());
			if (StringUtils.hasText(campaign.getMainPolicyId())) {
				policy = policies.get(tenant(), campaign.getMainPolicyId()); policyItems = policies.listItems(tenant(), campaign.getMainPolicyId());
				if (policy == null || !com.aryn.cloud.promotion.api.entity.PricePolicy.STATUS_DRAFT.equals(policy.getStatus())
						|| !campaign.getMainPolicyDigest().equals(digest(Arrays.asList(policy, policyItems)))) {
					throw new ArynBusinessException(409,"协议草稿已被独立修改或发布，请重新核定");
				}
				request.setExpectedRevision(campaign.getRevision());
			}
		}
		PromotionActivity draft;
		if (policy == null) { draft = commands.get(request.getDraftActivityId()); B2bActivityAdmission.requireManual(draft); }
		else {
			// 仅供共同的展示/风险字段，不入统一活动表、不走活动类型准入。
			draft = new PromotionActivity(); draft.setId(policy.getId()); draft.setActivityType("POLICY");
			draft.setTenantId(tenant()); draft.setRevision(request.getExpectedRevision()); draft.setStatus(PromotionActivity.STATUS_DRAFT);
			draft.setScopeType("2"); draft.setScopeValue(String.join(",", definition.getSkuIds()));
			draft.setStartTime(policy.getStartTime()); draft.setEndTime(policy.getEndTime());
		}
		if (!request.getExpectedRevision().equals(draft.getRevision())
				|| PromotionActivity.STATUS_PUBLISHED.equals(draft.getStatus())) {
			throw new ArynBusinessException(409, "请选择当前修订的未发布草稿");
		}
		if (policy == null) { governance.validateRules(draft); }
		String draftDigest = sourceDigest(actorId, request);
		PromotionActivity replacement = null;
		if (StringUtils.hasText(request.getReplacesActivityId())) {
			if (policy != null) { throw new ArynBusinessException("协议政策不接受统一活动替换标识"); }
			replacement = commands.get(request.getReplacesActivityId());
			B2bActivityAdmission.requireManual(replacement);
			if (draft.getId().equals(replacement.getId()) || !PromotionActivity.STATUS_PUBLISHED.equals(replacement.getStatus())
					|| !draft.getActivityType().equals(replacement.getActivityType())
					|| !Objects.equals(request.getReplacesRevision(), replacement.getRevision())) {
				throw new ArynBusinessException(409, "替换对象必须为已查看修订的同类型已发布活动");
			}
		}
		request.getSample().setTenantId(tenant());
		// RPC在引擎读取合同价之前验证样本用户/主体，售价与港口也由事实域重建。
		var orderFacts = remoteOrderScenarioService.capture(request.getSample(), request.getCouponUserId());
		if (orderFacts == null || orderFacts.getContext() == null
				|| !tenant().equals(orderFacts.getContext().getTenantId())) {
			throw new ArynBusinessException("无法捕获有效订单事实");
		}
		request.setSample(orderFacts.getContext());
		if (definition != null && definition.getCoupon() != null) {
			orderFacts.setProposedCoupon(ephemeralCoupon(definition, orderFacts.getContext(), request.getCampaignDraftId()));
		}
		var facts = policy == null ? engine.captureScenario(request.getSample(), draft, replacement)
				: engine.capturePolicyScenario(request.getSample(), policy, policyItems);
		CampaignSimulationVO result = new CampaignSimulationVO();
		result.setCampaignDraftId(request.getCampaignDraftId());
		result.setExpectedCampaignRevision(request.getExpectedCampaignRevision());
		result.setDraftActivityId(policy == null ? draft.getId() : null);
		result.setDraftRevision(draft.getRevision());
		result.setAsOf(facts.proposed().asOf());
		result.setBaseline(new PromotionScenarioEvaluator(facts.baseline()).evaluate());
		result.setProposed(new PromotionScenarioEvaluator(facts.proposed()).evaluate());
		result.setOrderComparison(remoteOrderScenarioService.compare(orderFacts, result.getBaseline(), result.getProposed()));
		if (result.getOrderComparison() == null || result.getOrderComparison().getBaseline() == null
				|| result.getOrderComparison().getProposed() == null) {
			throw new ArynBusinessException("订单域未返回完整比较结果");
		}
		var assumptions = new CampaignSimulationVO.Assumptions();
		assumptions.setScopeType(draft.getScopeType());
		assumptions.setScopeValue(draft.getScopeValue());
		assumptions.setStartTime(draft.getStartTime());
		assumptions.setEndTime(draft.getEndTime());
		assumptions.setTotalBudget(draft.getTotalBudget());
		assumptions.setConsumedBudget(replacement == null ? draft.getConsumedBudget() : replacement.getConsumedBudget());
		assumptions.setTotalQuota(draft.getTotalQuota());
		assumptions.setConsumedQuota(replacement == null ? draft.getConsumedQuota() : replacement.getConsumedQuota());
		assumptions.setPerUserLimit(draft.getPerUserLimit());
		assumptions.setUserUsage(facts.proposed().userUsage().get(draft.getId()));
		assumptions.setGiftStock(draft.getGiftStock());
		assumptions.setGiftUsage(facts.proposed().giftUsage().get(draft.getId()));
		assumptions.setMonthlyUsage(facts.proposed().monthlyUsage().get(draft.getId()));
		assumptions.setOverlappingActivities(facts.baseline().activities().stream()
			.filter(activity -> draft.getActivityType().equals(activity.getActivityType()))
			.map(PromotionActivity::getId).toList());
		result.setAssumptions(assumptions);
		List<CampaignSimulationVO.Risk> risks = new ArrayList<>();
		if (policy != null) { risks.add(risk("POLICY_BUDGET_NOT_ENFORCED", null, "正式协议政策没有累计预算拦截；活动包预算不能视为合同价自动停用上限，发布需显式确认")); }
		List<CampaignSimulationVO.SkuFact> skuFacts = new ArrayList<>();
		Map<String, Integer> quantity = request.getSample().getSkuItems().stream().collect(Collectors.toMap(
				com.aryn.cloud.promotion.api.dto.PromotionContextDTO.SkuItem::getSkuId,
				com.aryn.cloud.promotion.api.dto.PromotionContextDTO.SkuItem::getQuantity));
		for (GoodsSku sku : orderFacts.getSkus()) {
			int qty = quantity.get(sku.getId());
			BigDecimal cost = sku.getCostPrice() != null ? sku.getCostPrice()
					: sku.getGoodsSpu() == null ? null : sku.getGoodsSpu().getCostPrice();
			var fact = new CampaignSimulationVO.SkuFact();
			fact.setSkuId(sku.getId());
			fact.setStock(sku.getStock());
			fact.setSampleQuantity(qty);
			boolean known = cost != null && cost.signum() >= 0;
			fact.setCostStatus(known ? "KNOWN" : "UNKNOWN");
			fact.setCostLower(known ? cost : null);
			fact.setCostUpper(known ? cost : null);
			skuFacts.add(fact);
			if (!known) { risks.add(risk("COST_UNKNOWN", sku.getId(), "缺少成本事实，不能判断利润")); }
			else {
				BigDecimal payable = result.getOrderComparison().getProposed().getLines().stream()
						.filter(line -> sku.getId().equals(line.getSkuId()))
						.map(com.aryn.cloud.promotion.api.vo.CampaignOrderComparisonVO.Line::getGoodsPayable)
						.reduce(BigDecimal.ZERO, BigDecimal::add);
				if (payable.compareTo(cost.multiply(BigDecimal.valueOf(qty))) < 0) {
					risks.add(risk("BELOW_COST", sku.getId(), "样本券后商品应付低于已知成本"));
				}
			}
			if (sku.getStock() == null) { risks.add(risk("STOCK_UNKNOWN", sku.getId(), "库存未知，模拟不保留库存")); }
			else if (sku.getStock() < qty) { risks.add(risk("STOCK_SHORT", sku.getId(), "当前库存不足样本数量")); }
		}
		List<String> giftIds = result.getProposed().getGifts().stream().map(gift -> gift.getSkuId()).distinct().toList();
		List<GoodsSku> gifts = giftIds.isEmpty() ? List.of() : remoteGoodsSkuService.getBySkuIds(giftIds);
		Map<String, GoodsSku> giftById = gifts.stream().filter(sku -> tenant().equals(sku.getTenantId()))
				.collect(Collectors.toMap(GoodsSku::getId, sku -> sku));
		for (var gift : result.getProposed().getGifts()) {
			GoodsSku sku = giftById.get(gift.getSkuId());
			if (sku == null || sku.getStock() == null || sku.getStock() < gift.getQuantity()) {
				risks.add(risk("GIFT_STOCK_UNPROVEN", gift.getSkuId(), "赠品库存不足或无法核实"));
			}
			risks.add(risk("GIFT_COST_SEPARATE", gift.getSkuId(), "赠品成本与履约费用需另行核定"));
		}
		if (result.getProposed().getDetails().stream().noneMatch(detail -> draft.getId().equals(detail.getActivityId()))) {
			risks.add(risk("DRAFT_NOT_SELECTED", null, "草稿未在样本生效：核对时点、适用范围、门槛、额度和更优方案"));
		}
		if (StringUtils.hasText(result.getOrderComparison().getProposed().getCouponReason())) {
			risks.add(risk("COUPON_NOT_APPLIED", null, result.getOrderComparison().getProposed().getCouponReason()));
		}
		risks.add(risk("CURRENT_FACT_ASSUMPTION", null, "未来情景沿用当前库存、成员和权益事实；不预占、不预测销量"));
		risks.add(risk("EXCLUDES_FREIGHT_POINTS", null, "报价仅含券后商品应付，不含地址运费、积分及储值扣款"));
		result.setRisks(risks);
		result.setSkuFacts(skuFacts);
		result.setInputDigest(digest(request));
		result.setFactDigest(digest(Arrays.asList(snapshotValues(facts.baseline()), snapshotValues(facts.proposed()),
				orderFacts, gifts, replacement)));
		result.setCurrent(true);
		return new Calculation(request, draftDigest, result);
	}

	private String sourceDigest(String actor, CampaignSimulationDTO request) {
		if (!StringUtils.hasText(request.getCampaignDraftId())) { return digest(commands.get(request.getDraftActivityId())); }
		var campaign = campaignDrafts.owned(actor, request.getCampaignDraftId(), false);
		Object primary;
		if (StringUtils.hasText(campaign.getMainPolicyId())) {
			primary = Arrays.asList(policies.get(tenant(), campaign.getMainPolicyId()), policies.listItems(tenant(), campaign.getMainPolicyId()));
		}
		else { primary = commands.get(request.getDraftActivityId()); }
		// 不含绑定模拟ID等展示状态，否则绑定结果本身会使凭据失效。
		return digest(Arrays.asList(primary, campaign.getId(), campaign.getRevision(), campaign.getDefinitionJson(),
			campaign.getStatus(), campaign.getMainActivityId(), campaign.getMainActivityRevision(), campaign.getMainPolicyId(), campaign.getMainPolicyDigest()));
	}

	private void validateSample(com.aryn.cloud.promotion.api.dto.CampaignDraftSaveDTO definition, CampaignSimulationDTO request) {
		var sample = request.getSample();
		if (!Objects.equals(definition.getSampleUserId(), sample.getUserId())
				|| !Objects.equals(definition.getPurchaseScene(), sample.getPurchaseScene())
				|| !Objects.equals(definition.getVesselId(), sample.getVesselId())
				|| !Objects.equals(definition.getBuyerOrgId(), sample.getBuyerOrgId())
				|| !Objects.equals(definition.getVesselCallId(), sample.getVesselCallId())
				|| sample.getSkuItems() == null || sample.getSkuItems().isEmpty()
				|| sample.getSkuItems().stream().anyMatch(item -> !definition.getSkuIds().contains(item.getSkuId()))) {
			throw new ArynBusinessException("模拟样本必须使用活动包已核定主体和选品");
		}
		if (sample.getActivityTime() == null || sample.getActivityTime().isBefore(definition.getStartTime())
				|| sample.getActivityTime().isAfter(definition.getEndTime())) {
			throw new ArynBusinessException("模拟时点必须位于活动包窗口内");
		}
	}

	private com.aryn.cloud.promotion.api.vo.CouponUserRespVO ephemeralCoupon(
			com.aryn.cloud.promotion.api.dto.CampaignDraftSaveDTO definition,
			com.aryn.cloud.promotion.api.dto.PromotionContextDTO context, String campaignId) {
		var rule = definition.getCoupon();
		var info = new com.aryn.cloud.promotion.api.entity.CouponInfo();
		info.setId("draft:" + campaignId); info.setCouponName(rule.getName()); info.setCouponType("1");
		info.setAmount(rule.getAmount()); info.setThreshold(rule.getThreshold()); info.setUseRange("1");
		info.setStatus("0"); info.setTenantId(tenant()); info.setTotalNum(rule.getQuantity()); info.setRemainNum(rule.getQuantity());
		var coupon = new com.aryn.cloud.promotion.api.vo.CouponUserRespVO();
		coupon.setId(info.getId()); coupon.setCouponId(info.getId()); coupon.setCouponInfo(info);
		coupon.setUserId(context.getUserId()); coupon.setTenantId(tenant()); coupon.setStatus("0");
		coupon.setValidatTime(definition.getEndTime()); return coupon;
	}

	private Object snapshotValues(PromotionFactSnapshot facts) {
		Map<String, Boolean> newbies = new TreeMap<>();
		facts.newbieEligibility().forEach((key, value) -> newbies.put(String.valueOf(key), value));
		return Arrays.asList(facts.context(), facts.asOf(), facts.activities(), facts.policies(), facts.policyItems(),
				facts.giftPrices(), new TreeSet<>(facts.frequentSkuIds()), facts.memberLevels(), facts.firstOrderEligibility(),
				newbies, facts.monthlyUsage(), facts.userUsage(), facts.giftUsage());
	}

	private CampaignSimulationRecord owned(String actor, String id) {
		requireActor(actor);
		CampaignSimulationRecord record = mapper.selectOne(Wrappers.lambdaQuery(CampaignSimulationRecord.class)
				.eq(CampaignSimulationRecord::getId, id).eq(CampaignSimulationRecord::getTenantId, tenant())
				.eq(CampaignSimulationRecord::getActorId, actor));
		if (record == null || !tenant().equals(record.getTenantId()) || !actor.equals(record.getActorId())) {
			throw new ArynBusinessException("模拟记录不存在");
		}
		return record;
	}

	private CampaignSimulationVO stale(CampaignSimulationVO result, String reason) {
		result.setCurrent(false);
		result.setStaleReason(reason);
		return result;
	}

	private CampaignSimulationVO.Risk risk(String code, String sku, String message) {
		return new CampaignSimulationVO.Risk(code, sku, message);
	}

	private String tenant() {
		String tenant = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenant)) { throw new ArynBusinessException("缺少租户上下文"); }
		return tenant;
	}

	private void requireActor(String actor) {
		tenant();
		if (!StringUtils.hasText(actor)) { throw new ArynBusinessException("缺少操作者"); }
	}

	private String digest(Object value) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(json(value).getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		}
		catch (Exception invalid) { throw new IllegalStateException("无法生成模拟摘要", invalid); }
	}

	private ObjectMapper codec() {
		return objectMapper.copy().configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
				.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
	}

	private String json(Object value) {
		try { return codec().writeValueAsString(value); }
		catch (Exception invalid) { throw new IllegalStateException("无法序列化模拟事实", invalid); }
	}

	private <T> T read(String value, Class<T> type) {
		try { return codec().readValue(value, type); }
		catch (Exception invalid) { throw new ArynBusinessException("模拟数据格式错误"); }
	}

	private record Calculation(CampaignSimulationDTO request, String draftDigest, CampaignSimulationVO result) { }
}
