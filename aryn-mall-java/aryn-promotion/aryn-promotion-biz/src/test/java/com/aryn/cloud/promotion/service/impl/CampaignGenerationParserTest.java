package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.promotion.api.dto.*;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CampaignGenerationParserTest {
	private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
	private final CampaignProposalValidator validator = new CampaignProposalValidator(new ActivityPublishGovernanceService(mock(PromotionActivityMapper.class)));
	private final CampaignGenerationParser parser = new CampaignGenerationParser(validator, json);

	@Test void validLadderUsesOnlySelectedSkuAndServerTimeScopeAndBudget() throws Exception {
		var input = input();
		var result = parser.parse(input, json.readTree("{\"activityType\":\"4\",\"name\":\"阶梯采购\",\"rules\":{\"skuLadders\":[{\"skuId\":\"sku1\",\"ladders\":[{\"minQty\":3,\"unitPrice\":90}]}]},\"reason\":\"依据已选商品和上限\"}"));
		assertEquals("2", result.activity().getScopeType()); assertEquals("sku1", result.activity().getScopeValue());
		assertEquals(input.getConstraints().getStartTime(), result.activity().getStartTime());
		assertEquals(input.getConstraints().getBudget(), result.activity().getTotalBudget());
	}

	@Test void retiredTypesCandidateEscapesAndExecutableOutputAreRejected() throws Exception {
		assertThrows(RuntimeException.class, () -> parse("{\"activityType\":\"9\",\"name\":\"新人价\",\"rules\":{},\"reason\":\"x\"}"));
		assertThrows(RuntimeException.class, () -> parse("{\"activityType\":\"4\",\"name\":\"x\",\"rules\":{\"skuLadders\":[{\"skuId\":\"outside\",\"ladders\":[{\"minQty\":1,\"unitPrice\":90}]}]},\"reason\":\"x\"}"));
		assertThrows(RuntimeException.class, () -> parse(valid().replace("\"reason\":", "\"components\":[{\"type\":\"custom-html\"}],\"reason\":")));
		assertThrows(RuntimeException.class, () -> parse(valid().replace("\"tiers\":", "\"script\":\"alert(1)\",\"tiers\":")));
	}

	@Test void modelCannotChangeTenantTimeOrBusinessSubject() {
		for (String field : List.of("tenantId", "startTime", "buyerOrgId", "url", "status")) {
			assertThrows(RuntimeException.class, () -> parse(valid().replace("\"reason\":", "\""+field+"\":\"forged\",\"reason\":")));
		}
	}

	@Test void excessiveDiscountAndCouponExposureAreRejected() {
		assertThrows(RuntimeException.class, () -> parse(valid().replace("\"discountAmount\":10", "\"discountAmount\":50")));
		assertThrows(RuntimeException.class, () -> parse(valid().replace("\"reason\":", "\"coupon\":{\"name\":\"定向满减\",\"amount\":10,\"threshold\":100,\"quantity\":101},\"reason\":")));
	}

	@Test void couponExposureIsReservedFromPrimaryBudgetAndCannotMakeItUnlimited() throws Exception {
		String output = valid().replace("\"reason\":", "\"coupon\":{\"name\":\"定向满减\",\"amount\":10,\"threshold\":100,\"quantity\":20},\"reason\":");
		assertEquals(0, new BigDecimal("800").compareTo(parse(output).activity().getTotalBudget()));
		assertThrows(RuntimeException.class, () -> parse(output.replace("\"quantity\":20", "\"quantity\":100")));
		assertThrows(RuntimeException.class, () -> parse(output.replace("\"tiers\":", "\"monthlyBudget\":900,\"tiers\":")));
		String gift = "{\"activityType\":\"5\",\"name\":\"采购赠品\",\"giftStock\":10,\"rules\":{\"minAmount\":1000,\"gifts\":[{\"skuId\":\"sku1\",\"quantity\":1}]},\"coupon\":{\"name\":\"定向券\",\"amount\":10,\"threshold\":100,\"quantity\":50},\"reason\":\"总预算校验\"}";
		assertThrows(RuntimeException.class, () -> parse(gift));
	}

	@Test void campaignTargetIsRebuiltFromAuthorizedDefinitionAndCombinedDiscountIsBounded() throws Exception {
		var input=input();input.getConstraints().setPurchaseScene("3");input.getConstraints().setBuyerOrgId("org");
		var proposal=parser.parse(input,json.readTree(valid()));
		assertEquals("org",json.readTree(proposal.activity().getRules()).get("campaignTarget").get("buyerOrgId").asText());
		assertThrows(RuntimeException.class,()->parse(valid().replace("\"tiers\":","\"campaignTarget\":{\"buyerOrgId\":\"forged\"},\"tiers\":")));
		String both=valid().replace("\"discountAmount\":10","\"discountAmount\":15").replace("\"reason\":","\"coupon\":{\"name\":\"定向券\",\"amount\":10,\"threshold\":100,\"quantity\":10},\"reason\":");
		assertThrows(RuntimeException.class,()->parse(both));
	}

	@Test void numericCoercionAndFractionalQuantitiesAreRejected() {
		assertThrows(RuntimeException.class,()->parse(valid().replace("\"discountAmount\":10","\"discountAmount\":\"10\"")));
		assertThrows(RuntimeException.class,()->parse(valid().replace("\"discountAmount\":10","\"discountAmount\":10.001")));
		assertThrows(RuntimeException.class,()->parse(valid().replace("\"minAmount\":100","\"minQty\":1.5")));
	}

	@Test void calendarSpecialUsesRealPriceFieldAndRequiresCalendar() throws Exception {
		String output = "{\"activityType\":\"12\",\"name\":\"档期特供\",\"rules\":{\"skus\":[{\"skuId\":\"sku1\",\"price\":90}]},\"reason\":\"真实档期\"}";
		assertThrows(RuntimeException.class, () -> parser.parse(input(), json.readTree(output)));
		var input = input(); input.getConstraints().setCalendarId("calendar");
		var proposal = parser.parse(input, json.readTree(output));
		assertEquals("calendar", proposal.activity().getCalendarId());
		assertThrows(RuntimeException.class, () -> parser.parse(input, json.readTree(output.replace("\"price\"", "\"specialPrice\""))));
	}

	@Test void giftRequiresKnownCostStockAndBoundedTotalCost() throws Exception {
		String output = "{\"activityType\":\"5\",\"name\":\"采购赠品\",\"giftStock\":10,\"rules\":{\"minAmount\":1000,\"gifts\":[{\"skuId\":\"sku1\",\"quantity\":1}]},\"reason\":\"有库存和成本证据\"}";
		assertNotNull(parser.parse(input(), json.readTree(output)));
		var unknown = input(); unknown.getCandidates().get(0).setCostPrice(null);
		assertThrows(RuntimeException.class, () -> parser.parse(unknown, json.readTree(output)));
		assertThrows(RuntimeException.class, () -> parser.parse(input(), json.readTree(output.replace("\"giftStock\":10", "\"giftStock\":20"))));
	}

	@Test void missingBusinessInputsAreReportedAndInvalidWindowsCannotProceed() {
		var empty = new CampaignDraftSaveDTO();
		assertTrue(validator.missing(empty).containsAll(List.of("intent", "sampleUserId", "skuIds", "budget", "maxDiscountPercent", "startTime", "endTime")));
		empty.setPrimaryType("12"); assertTrue(validator.missing(empty).contains("calendarId"));
		var input = input(); input.getConstraints().setEndTime(input.getConstraints().getStartTime());
		assertThrows(RuntimeException.class, () -> validator.validateMetadata(input.getConstraints()));
	}

	private CampaignGenerationParser.Proposal parse(String output) throws Exception { return parser.parse(input(), json.readTree(output)); }
	private String valid() { return "{\"activityType\":\"7\",\"name\":\"采购满减\",\"rules\":{\"tiers\":[{\"minAmount\":100,\"discountAmount\":10}]},\"reason\":\"依据预算生成\"}"; }
	private CampaignGenerationInput input() {
		var def = new CampaignDraftSaveDTO(); def.setIntent("现行B2B采购活动"); def.setTitle("采购活动"); def.setPurchaseScene("1"); def.setSampleUserId("buyer");
		def.setSkuIds(List.of("sku1", "sku2")); def.setStartTime(LocalDateTime.of(2031, 1, 1, 0, 0)); def.setEndTime(LocalDateTime.of(2031, 3, 1, 0, 0));
		def.setBudget(new BigDecimal("1000")); def.setMaxDiscountPercent(new BigDecimal("20"));
		var input = new CampaignGenerationInput(); input.setConstraints(def);
		input.setCandidates(List.of(candidate("sku1"), candidate("sku2"))); return input;
	}
	private CampaignGenerationInput.Candidate candidate(String id) {
		var candidate = new CampaignGenerationInput.Candidate(); candidate.setSkuId(id); candidate.setSalesPrice(new BigDecimal("100"));
		candidate.setCostPrice(new BigDecimal("60")); candidate.setStock(100); return candidate;
	}
}
