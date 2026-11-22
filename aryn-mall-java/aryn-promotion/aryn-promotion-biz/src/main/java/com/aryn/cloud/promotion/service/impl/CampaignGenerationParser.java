package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Set;

/** 模型仅提出白名单规则，日期/租户/主体/范围/预算从服务端约束生成。 */
@Component
@RequiredArgsConstructor
public class CampaignGenerationParser {
	private final CampaignProposalValidator validator;
	private final ObjectMapper objectMapper;

	public Proposal parse(CampaignGenerationInput input, JsonNode output) {
		fields(output, Set.of("activityType", "name", "rules", "reason", "coupon", "giftStock", "totalQuota", "perUserLimit",
				"couponStackable", "pointsStackable"));
		String type = text(output, "activityType");
		JsonNode rules = output.get("rules");
		if (!B2bActivityAdmission.MANUAL_TYPES.contains(type)) { throw new ArynBusinessException("模型提出停运或不支持的活动类型"); }
		CampaignRuleSchema.validate(type, rules);
		var suggestion = new B2bActivitySaveDTO(); suggestion.setActivityType(type);
		suggestion.setActivityName(text(output, "name")); suggestion.setRules(rules.toString());
		suggestion.setGiftStock(integer(output, "giftStock")); suggestion.setTotalQuota(integer(output, "totalQuota"));
		suggestion.setPerUserLimit(integer(output, "perUserLimit"));
		suggestion.setCouponStackable(output.hasNonNull("couponStackable") ? text(output, "couponStackable") : "1");
		suggestion.setPointsStackable(output.hasNonNull("pointsStackable") ? text(output, "pointsStackable") : "1");
		CampaignCouponDraftDTO coupon = null;
		if (output.hasNonNull("coupon")) {
			var value = output.get("coupon"); fields(value, Set.of("name", "amount", "threshold", "quantity"));
			CampaignRuleSchema.money(value.get("amount")); CampaignRuleSchema.money(value.get("threshold")); CampaignRuleSchema.quantity(value.get("quantity"));
			try { coupon = objectMapper.treeToValue(value, CampaignCouponDraftDTO.class); }
			catch (Exception invalid) { throw new ArynBusinessException("模型定向券格式非法"); }
			validator.validateCoupon(input.getConstraints(), coupon);
		}
		var normalized = validator.normalize(input, suggestion, coupon);
		String reason = text(output, "reason");
		if (reason.length() > 1000) { throw new ArynBusinessException("模型理由超长"); }
		return new Proposal(normalized, coupon, reason);
	}

	private void fields(JsonNode node, Set<String> allowed) {
		if (node == null || !node.isObject()) { throw new ArynBusinessException("模型输出必须是规定的JSON对象"); }
		node.fieldNames().forEachRemaining(name -> {
			if (!allowed.contains(name)) { throw new ArynBusinessException("模型包含未允许字段：" + name); }
		});
	}
	private void array(JsonNode node) {
		if (node == null || !node.isArray() || node.isEmpty() || node.size() > 100) { throw new ArynBusinessException("模型规则列表为空或超长"); }
	}
	private String text(JsonNode node, String name) {
		JsonNode field = node.get(name);
		if (field == null || !field.isTextual() || field.asText().isBlank()) { throw new ArynBusinessException("模型缺少文本字段：" + name); }
		return field.asText();
	}
	private Integer integer(JsonNode node, String name) {
		JsonNode field = node.get(name);
		if (field == null || field.isNull()) { return null; }
		if (!field.isIntegralNumber() || !field.canConvertToInt() || field.intValue() < 0) { throw new ArynBusinessException("模型整数范围非法：" + name); }
		return field.intValue();
	}
	public record Proposal(B2bActivitySaveDTO activity, CampaignCouponDraftDTO coupon, String reason) { }
}
