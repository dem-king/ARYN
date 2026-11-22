package com.aryn.cloud.promotion.service.impl;
import com.aryn.cloud.promotion.api.dto.CampaignPublishCommandDTO;
import com.aryn.cloud.promotion.api.vo.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class CampaignPublishConfirmationTest {
 @Test void policyWithoutBudgetEnforcementRequiresExplicitAcceptanceAndShortStockAlwaysBlocks(){
  var request=new CampaignPublishCommandDTO();request.setExpectedPageRevision(1L);request.setPageDigest("page");request.setInputDigest("input");request.setFactDigest("facts");
  var preview=new CampaignPagePreviewVO();preview.setCurrent(true);preview.setPageDigest("page");var page=new PageDesignEditorVO();page.setDraftRevision(1L);preview.setPage(page);
  var simulation=new CampaignSimulationVO();simulation.setInputDigest("input");simulation.setFactDigest("facts");simulation.setRisks(List.of(new CampaignSimulationVO.Risk("POLICY_BUDGET_NOT_ENFORCED",null,"无累计预算")));preview.setSimulation(simulation);
  assertThrows(RuntimeException.class,()->CampaignPublishService.requireConfirmation(request,preview));
  request.setAcceptedRiskCodes(Set.of("POLICY_BUDGET_NOT_ENFORCED"));assertDoesNotThrow(()->CampaignPublishService.requireConfirmation(request,preview));
  simulation.setRisks(List.of(new CampaignSimulationVO.Risk("STOCK_SHORT","sku","库存不足")));request.setAcceptedRiskCodes(Set.of("STOCK_SHORT"));
  assertThrows(RuntimeException.class,()->CampaignPublishService.requireConfirmation(request,preview));
 }
}
