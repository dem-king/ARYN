package com.aryn.cloud.promotion.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.remote.RemoteOrderScenarioService;
import com.aryn.cloud.promotion.api.dto.*;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** 模型、人工保存共用的活动包边界；只把服务端核实的SKU和明确业务约束交给模型。 */
@Service
@RequiredArgsConstructor
public class CampaignProposalValidator {
	private final ActivityPublishGovernanceService governance;
	@DubboReference private RemoteOrderScenarioService remoteOrderScenarioService;

	public List<String> missing(CampaignDraftSaveDTO definition) {
		List<String> fields = new ArrayList<>();
		if (!StringUtils.hasText(definition.getIntent())) { fields.add("intent"); }
		if (!List.of("1", "2", "3").contains(Objects.toString(definition.getPurchaseScene(), ""))) { fields.add("purchaseScene"); }
		if (!StringUtils.hasText(definition.getSampleUserId())) { fields.add("sampleUserId"); }
		if ("2".equals(definition.getPurchaseScene()) && !StringUtils.hasText(definition.getVesselId())) { fields.add("vesselId"); }
		if ("3".equals(definition.getPurchaseScene()) && !StringUtils.hasText(definition.getBuyerOrgId())) { fields.add("buyerOrgId"); }
		if (definition.getSkuIds() == null || definition.getSkuIds().isEmpty()) { fields.add("skuIds"); }
		if (definition.getStartTime() == null) { fields.add("startTime"); }
		if (definition.getEndTime() == null) { fields.add("endTime"); }
		if (definition.getBudget() == null) { fields.add("budget"); }
		if (definition.getMaxDiscountPercent() == null) { fields.add("maxDiscountPercent"); }
		if ("12".equals(definition.getPrimaryType()) && !StringUtils.hasText(definition.getCalendarId())) { fields.add("calendarId"); }
		return fields;
	}

	public void validateMetadata(CampaignDraftSaveDTO definition) {
		if (definition == null) { throw new ArynBusinessException("活动包不能为空"); }
		if (StringUtils.hasText(definition.getPrimaryType()) && !B2bActivityAdmission.MANUAL_TYPES.contains(definition.getPrimaryType()) && !"POLICY".equals(definition.getPrimaryType())) {
			throw new ArynBusinessException("仅支持现行B2B主活动；协议政策请使用显式政策命令");
		}
		if (definition.getIntent() != null && definition.getIntent().length() > 4000
				|| definition.getTitle() != null && definition.getTitle().length() > 100) {
			throw new ArynBusinessException("意图或标题超长");
		}
		if (definition.getSkuIds() != null && (definition.getSkuIds().size() > 100
				|| definition.getSkuIds().stream().anyMatch(id -> !StringUtils.hasText(id) || id.length() > 64)
				|| new HashSet<>(definition.getSkuIds()).size() != definition.getSkuIds().size())) {
			throw new ArynBusinessException("候选SKU必须唯一且最多100个");
		}
		if (definition.getBudget() != null && (definition.getBudget().signum() <= 0 || definition.getBudget().scale() > 2
				|| definition.getBudget().compareTo(new BigDecimal("99999999.99")) > 0)) {
			throw new ArynBusinessException("活动包预算必须为正数，最多两位小数");
		}
		if (definition.getMaxDiscountPercent() != null && (definition.getMaxDiscountPercent().scale() > 4 || definition.getMaxDiscountPercent().signum() <= 0
				|| definition.getMaxDiscountPercent().compareTo(new BigDecimal("100")) > 0)) {
			throw new ArynBusinessException("最大让利比例必须在(0,100]范围");
		}
		if (definition.getStartTime() != null && definition.getEndTime() != null
				&& !definition.getStartTime().isBefore(definition.getEndTime())) {
			throw new ArynBusinessException("活动包开始时间必须早于结束时间");
		}
	}

	/** 只读外呼，不应放入活动包写事务；此处只读取候选，成交样本数量另由正式度量规则校验。 */
	public CampaignGenerationInput prepare(CampaignDraftSaveDTO definition) {
		validateMetadata(definition);
		if (!missing(definition).isEmpty()) { throw new ArynBusinessException("请补齐业务参数：" + missing(definition)); }
		if (!definition.getEndTime().isAfter(LocalDateTime.now())) { throw new ArynBusinessException("活动包结束时间已过"); }
		PromotionContextDTO context = new PromotionContextDTO();
		context.setTenantId(ArynTenantContextHolder.getTenantId());
		context.setUserId(definition.getSampleUserId()); context.setPurchaseScene(definition.getPurchaseScene());
		context.setVesselId(definition.getVesselId()); context.setBuyerOrgId(definition.getBuyerOrgId());
		context.setVesselCallId(definition.getVesselCallId()); context.setActivityTime(definition.getStartTime());
		context.setSkuItems(definition.getSkuIds().stream().map(id -> {
			var item = new PromotionContextDTO.SkuItem(); item.setSkuId(id); item.setQuantity(1); return item;
		}).toList());
		var facts = remoteOrderScenarioService.capture(context, null);
		var input = new CampaignGenerationInput(); input.setConstraints(definition);
		input.setCandidates(facts.getSkus().stream().map(sku -> {
			var candidate = new CampaignGenerationInput.Candidate(); candidate.setSkuId(sku.getId());
			candidate.setSpuId(sku.getSpuId()!=null?sku.getSpuId():sku.getGoodsSpu()==null?null:sku.getGoodsSpu().getId());
			candidate.setName(sku.getGoodsSpu() == null ? sku.getId() : sku.getGoodsSpu().getName());
			candidate.setSalesPrice(sku.getSalesPrice()); candidate.setStock(sku.getStock());
			candidate.setMinOrderQty(sku.getMinOrderQty()); candidate.setStepQty(sku.getStepQty());
			candidate.setSalesUnit(sku.getSalesUnit());
			candidate.setCostPrice(sku.getCostPrice() != null ? sku.getCostPrice()
					: sku.getGoodsSpu() == null ? null : sku.getGoodsSpu().getCostPrice());
			return candidate;
		}).toList());
		return input;
	}

	public B2bActivitySaveDTO normalize(CampaignGenerationInput input, B2bActivitySaveDTO suggestion) {
		if (suggestion == null) { throw new ArynBusinessException("缺少主活动方案"); }
		return normalize(input, suggestion, input.getConstraints().getCoupon());
	}

	public B2bActivitySaveDTO normalize(CampaignGenerationInput input, B2bActivitySaveDTO suggestion,
			CampaignCouponDraftDTO coupon) {
		if (suggestion == null) { throw new ArynBusinessException("缺少主活动方案"); }
		var original = input.getConstraints();
		validateCoupon(original, coupon);
		var definition = new CampaignDraftSaveDTO(); BeanUtils.copyProperties(original, definition);
		BigDecimal exposure = coupon == null ? BigDecimal.ZERO : coupon.getAmount().multiply(BigDecimal.valueOf(coupon.getQuantity()));
		definition.setBudget(original.getBudget().subtract(exposure));
		if(coupon!=null) {
			var couponPercent=coupon.getAmount().multiply(new BigDecimal("100")).divide(coupon.getThreshold(),8,java.math.RoundingMode.CEILING);
			definition.setMaxDiscountPercent(original.getMaxDiscountPercent().subtract(couponPercent));
			if(definition.getMaxDiscountPercent().signum()<=0){throw new ArynBusinessException("定向券让利比例已用尽整包让利上限");}
		}
		// 正式活动的0预算表示不限额，不能用0承载已被券全部占满的包预算。
		if (definition.getBudget().signum() <= 0) { throw new ArynBusinessException("定向券预留后必须保留正数主活动预算"); }
		B2bActivitySaveDTO result = new B2bActivitySaveDTO(); BeanUtils.copyProperties(suggestion, result);
		result.setStartTime(definition.getStartTime()); result.setEndTime(definition.getEndTime());
		result.setPurchaseScene(definition.getPurchaseScene());
		result.setScopeType("2"); result.setScopeValue(String.join(",", definition.getSkuIds()));
		result.setTotalBudget(definition.getBudget()); result.setExpectedRevision(null);
		result.setCalendarId(definition.getCalendarId());
		if (StringUtils.hasText(definition.getPrimaryType()) && !definition.getPrimaryType().equals(result.getActivityType())) {
			throw new ArynBusinessException("生成方案不能改变已选择的主活动类型");
		}
		// 已保存草稿含服务端主体限定，先剔除再从本次已核定主体重建，客户端不能换主体。
		var clean=JSONUtil.parseObj(result.getRules());clean.remove("campaignTarget");result.setRules(clean.toString());
		CampaignRuleSchema.validate(result.getActivityType(), result.getRules());
		JSONObject proposedRules = JSONUtil.parseObj(result.getRules());
		String skuKey = "4".equals(result.getActivityType()) ? "skuLadders" : "12".equals(result.getActivityType()) ? "skus" : null;
		if (skuKey != null && proposedRules.getJSONArray(skuKey) != null) {
			result.setScopeValue(proposedRules.getJSONArray(skuKey).stream()
				.map(row -> ((JSONObject) row).getStr("skuId")).collect(Collectors.joining(",")));
		}
		if (!StringUtils.hasText(result.getActivityName()) || result.getActivityName().length() > 100) {
			throw new ArynBusinessException("主活动名称不能为空或超长");
		}
		var activity = new com.aryn.cloud.promotion.api.entity.PromotionActivity(); BeanUtils.copyProperties(result, activity);
		B2bActivityAdmission.requireManual(activity); governance.validateRules(activity);
		Map<String, CampaignGenerationInput.Candidate> candidates = input.getCandidates().stream()
				.collect(Collectors.toMap(CampaignGenerationInput.Candidate::getSkuId, candidate -> candidate));
		JSONObject rules = JSONUtil.parseObj(result.getRules());
		if (rules.getBigDecimal("monthlyBudget") != null && rules.getBigDecimal("monthlyBudget").compareTo(definition.getBudget()) > 0) {
			throw new ArynBusinessException("月度额度不得超过活动包预算");
		}
		switch (result.getActivityType()) {
			case "4" -> {
				for (Object row : rules.getJSONArray("skuLadders")) {
					JSONObject sku = (JSONObject) row;
					var candidate = candidate(candidates, sku.getStr("skuId"));
					for (Object tier : sku.getJSONArray("ladders")) {
						boundedPrice(candidate.getSalesPrice(), ((JSONObject) tier).getBigDecimal("unitPrice"), definition);
					}
				}
			}
			case "12" -> {
				for (Object row : rules.getJSONArray("skus")) {
					JSONObject sku = (JSONObject) row;
					boundedPrice(candidate(candidates, sku.getStr("skuId")).getSalesPrice(), sku.getBigDecimal("price"), definition);
				}
			}
			case "7" -> {
				BigDecimal cheapest = input.getCandidates().stream().map(CampaignGenerationInput.Candidate::getSalesPrice)
						.min(BigDecimal::compareTo).orElseThrow();
				for (Object row : rules.getJSONArray("tiers")) {
					JSONObject tier = (JSONObject) row;
					BigDecimal base = tier.getBigDecimal("minAmount");
					if (base == null || base.signum() <= 0) { base = cheapest.multiply(BigDecimal.valueOf(tier.getInt("minQty", 0))); }
					boundedReduction(base, tier.getBigDecimal("discountAmount"), definition);
				}
			}
			case "5" -> {
				if (result.getGiftStock() == null || result.getGiftStock() < 1) { throw new ArynBusinessException("买赠必须明确赠品总件数上限"); }
				BigDecimal maxCost = BigDecimal.ZERO;
				BigDecimal giftValue = BigDecimal.ZERO;
				for (Object row : rules.getJSONArray("gifts")) {
					var gift = (JSONObject) row; var candidate = candidate(candidates, gift.getStr("skuId"));
					if (candidate.getStock() == null || candidate.getStock() < result.getGiftStock()
							|| candidate.getCostPrice() == null || candidate.getCostPrice().signum() < 0) {
						throw new ArynBusinessException("买赠需可核实赠品库存和成本，请先补齐商品事实");
					}
					int quantity = gift.getInt("quantity");
					if (quantity > result.getGiftStock() || quantity > candidate.getStock()) { throw new ArynBusinessException("赠品数量超过核实库存或活动上限"); }
					giftValue = giftValue.add(candidate.getSalesPrice().multiply(BigDecimal.valueOf(quantity)));
					maxCost = maxCost.max(candidate.getCostPrice());
				}
				boundedReduction(rules.getBigDecimal("minAmount"), giftValue, definition);
				if (maxCost.multiply(BigDecimal.valueOf(result.getGiftStock())).compareTo(definition.getBudget()) > 0) {
					throw new ArynBusinessException("赠品总件数按已知成本计算超过活动包预算");
				}
			}
			default -> throw new ArynBusinessException("不支持的主活动类型");
		}
		if (result.getTotalQuota() != null && result.getTotalQuota() < 0) { throw new ArynBusinessException("配额不得为负数"); }
		if (StringUtils.hasText(original.getVesselId()) || StringUtils.hasText(original.getBuyerOrgId())) {
			var target=new JSONObject();
			if(StringUtils.hasText(original.getVesselId())){target.set("vesselId",original.getVesselId());}
			if(StringUtils.hasText(original.getBuyerOrgId())){target.set("buyerOrgId",original.getBuyerOrgId());}
			rules.set("campaignTarget",target);result.setRules(rules.toString());
		}
		return result;
	}

	public void validateCoupon(CampaignDraftSaveDTO definition, CampaignCouponDraftDTO coupon) {
		if (coupon == null) { return; }
		if (!StringUtils.hasText(coupon.getName()) || coupon.getName().length() > 50
				|| coupon.getQuantity() == null || coupon.getQuantity() < 1 || coupon.getQuantity() > 100000) {
			throw new ArynBusinessException("定向券名称或计划份数无效");
		}
		if (coupon.getAmount() == null || coupon.getThreshold() == null
				|| coupon.getAmount().scale() > 2 || coupon.getThreshold().scale() > 2) {
			throw new ArynBusinessException("定向券金额和门槛最多两位小数");
		}
		boundedReduction(coupon.getThreshold(), coupon.getAmount(), definition);
		if (coupon.getAmount().multiply(BigDecimal.valueOf(coupon.getQuantity())).compareTo(definition.getBudget()) > 0) {
			throw new ArynBusinessException("定向券计划总额超过活动包预算");
		}
	}

	private CampaignGenerationInput.Candidate candidate(Map<String, CampaignGenerationInput.Candidate> candidates, String id) {
		var candidate = candidates.get(id);
		if (candidate == null) { throw new ArynBusinessException("模型引用了候选外SKU"); }
		return candidate;
	}
	private void boundedPrice(BigDecimal original, BigDecimal price, CampaignDraftSaveDTO definition) {
		if (price == null || price.signum() <= 0 || price.compareTo(original) >= 0) { throw new ArynBusinessException("活动单价必须大于0且低于当前售价"); }
		boundedReduction(original, original.subtract(price), definition);
	}
	private void boundedReduction(BigDecimal base, BigDecimal amount, CampaignDraftSaveDTO definition) {
		if (base == null || base.signum() <= 0 || amount == null || amount.signum() <= 0
				|| amount.compareTo(definition.getBudget()) > 0
				|| amount.multiply(new BigDecimal("100")).compareTo(base.multiply(definition.getMaxDiscountPercent())) > 0) {
			throw new ArynBusinessException("让利超出已明确的比例或预算");
		}
	}
}
