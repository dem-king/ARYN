package com.aryn.cloud.promotion.service.impl;
import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CampaignTargetScopeTest {
	@Test void selectedSkusAndAuthorizedSubjectMustBothMatch() {
		var activity=new PromotionActivity();activity.setScopeType("2");activity.setScopeValue("sku");activity.setPurchaseScene("3");activity.setRules("{\"campaignTarget\":{\"buyerOrgId\":\"org\"}}");
		var context=new PromotionContextDTO();context.setPurchaseScene("3");context.setBuyerOrgId("other");var row=new PromotionContextDTO.SkuItem();row.setSkuId("sku");context.setSkuItems(List.of(row));
		assertFalse(PromotionScenarioEvaluator.matchesScope(activity,context,Set.of()));context.setBuyerOrgId("org");assertTrue(PromotionScenarioEvaluator.matchesScope(activity,context,Set.of()));
		row.setSkuId("outside");assertFalse(PromotionScenarioEvaluator.matchesScope(activity,context,Set.of()));row.setSkuId("sku");
		activity.setRules("{\"campaignTarget\":{}}");assertFalse(PromotionScenarioEvaluator.matchesScope(activity,context,Set.of()));
		activity.setRules("{\"campaignTarget\":{\"unknown\":\"org\"}}");assertFalse(PromotionScenarioEvaluator.matchesScope(activity,context,Set.of()));
	}
}
