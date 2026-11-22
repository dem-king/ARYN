package com.aryn.cloud.promotion.service.impl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.util.Set;

/** 人工/模型共用规则形状，拒绝未知字段、字符串金额、截断小数数量和超长数组。 */
final class CampaignRuleSchema {
	private CampaignRuleSchema() { }
	static void validate(String type, String value) {
		try { validate(type, new ObjectMapper().readTree(value)); }
		catch (ArynBusinessException rejected) { throw rejected; }
		catch (Exception invalid) { throw new ArynBusinessException("活动包规则必须是规定JSON"); }
	}
	static void validate(String type, JsonNode rules) {
		switch(type) {
			case "4" -> {
				fields(rules,Set.of("skuLadders","monthlyBudget")); array(rules.get("skuLadders"));
				for(var sku:rules.get("skuLadders")) {fields(sku,Set.of("skuId","ladders"));id(sku.get("skuId"));array(sku.get("ladders"));
					for(var tier:sku.get("ladders")) {fields(tier,Set.of("minQty","unitPrice"));quantity(tier.get("minQty"));money(tier.get("unitPrice"));}}
			}
			case "12" -> {
				fields(rules,Set.of("skus","monthlyBudget"));array(rules.get("skus"));for(var sku:rules.get("skus")){fields(sku,Set.of("skuId","price"));id(sku.get("skuId"));money(sku.get("price"));}
			}
			case "7" -> {
				fields(rules,Set.of("tiers","monthlyBudget"));array(rules.get("tiers"));
				for(var tier:rules.get("tiers")) {fields(tier,Set.of("minAmount","minQty","discountAmount"));money(tier.get("discountAmount"));
					if(!tier.hasNonNull("minAmount")&&!tier.hasNonNull("minQty")){reject();}
					if(tier.hasNonNull("minAmount")){money(tier.get("minAmount"));}if(tier.hasNonNull("minQty")){quantity(tier.get("minQty"));}}
			}
			case "5" -> {
				fields(rules,Set.of("minAmount","gifts","monthlyBudget"));money(rules.get("minAmount"));array(rules.get("gifts"));
				for(var gift:rules.get("gifts")){fields(gift,Set.of("skuId","quantity"));id(gift.get("skuId"));quantity(gift.get("quantity"));}
			}
			default -> reject();
		}
		if(rules.hasNonNull("monthlyBudget")){money(rules.get("monthlyBudget"));}
	}
	static void fields(JsonNode node,Set<String> allowed) {
		if(node==null||!node.isObject()){reject();}
		node.fieldNames().forEachRemaining(name->{if(!allowed.contains(name)){reject();}});
	}
	static void money(JsonNode value) {
		if(value==null||!value.isNumber()||value.decimalValue().scale()>2||value.decimalValue().signum()<=0||value.decimalValue().compareTo(new BigDecimal("99999999.99"))>0){reject();}
	}
	static void quantity(JsonNode value) {if(value==null||!value.isIntegralNumber()||!value.canConvertToInt()||value.intValue()<1){reject();}}
	private static void array(JsonNode node) {if(node==null||!node.isArray()||node.isEmpty()||node.size()>100){reject();}}
	private static void id(JsonNode node) {if(node==null||!node.isTextual()||node.asText().isBlank()||node.asText().length()>64){reject();}}
	private static void reject() {throw new ArynBusinessException("活动包规则包含未允许字段或非法数值类型、精度、范围");}
}
