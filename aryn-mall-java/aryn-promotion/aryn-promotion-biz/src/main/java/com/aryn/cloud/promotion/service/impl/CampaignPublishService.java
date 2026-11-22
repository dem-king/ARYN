package com.aryn.cloud.promotion.service.impl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.*;
import com.aryn.cloud.promotion.api.vo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
/** 人工命令可按持久信封重入；所有远程事实读取在写事务外，失败保留可恢复记录。 */
@Service
@RequiredArgsConstructor
public class CampaignPublishService {
 private final CampaignPublishStore store;
 private final CampaignPageDraftService pages;
 private final CampaignDraftService drafts;
 private final CampaignProposalValidator validator;
 public CampaignPublishVO publish(String actor,String id,CampaignPublishCommandDTO command) {
  var operation=store.begin(actor,id,command);
  if ("PUBLISHED".equals(operation.getStatus())) { return store.view(operation); }
  try {
   var definition=drafts.get(actor,id).getDefinition();
   var facts=validator.prepare(definition);
   if(definition.getActivity()!=null){validator.normalize(facts,definition.getActivity());}
   if(definition.getPolicy()!=null){
    var limit=definition.getMaxDiscountPercent();
    if(definition.getCoupon()!=null){var coupon=definition.getCoupon();limit=limit.subtract(coupon.getAmount().multiply(new java.math.BigDecimal("100")).divide(coupon.getThreshold(),8,java.math.RoundingMode.CEILING));}
    for(var item:definition.getPolicy().getItems()){
     var candidate=facts.getCandidates().stream().filter(c->c.getSkuId().equals(item.getSkuId())).findFirst().orElseThrow(()->new ArynBusinessException("协议SKU失效"));
     if(item.getPrice().compareTo(candidate.getSalesPrice())>=0||candidate.getSalesPrice().subtract(item.getPrice()).multiply(new java.math.BigDecimal("100")).compareTo(candidate.getSalesPrice().multiply(limit))>0){throw new ArynBusinessException("协议与券组合让利超限，请重新核定");}
    }
   }

   var pageCommand=new CampaignPageCommandDTO();pageCommand.setExpectedRevision(command.getExpectedRevision());
   pageCommand.setExpectedPageRevision(command.getExpectedPageRevision());pageCommand.setSimulationId(command.getSimulationId());
   var preview=pages.preview(actor,id,pageCommand);
   requireConfirmation(command,preview);
   return store.view(store.complete(actor,operation.getId(),command,preview,facts));
  } catch(RuntimeException failure) {
   store.fail(actor,operation.getId());
   throw failure;
  }
 }
 public CampaignPublishVO retry(String actor,String operationId) {
  var operation=store.owned(actor,operationId,false);
  return publish(actor,operation.getDraftId(),store.command(operation));
 }
 public List<CampaignPublishVO> list(String actor,String id){return store.list(actor,id);}
 static void requireConfirmation(CampaignPublishCommandDTO command,CampaignPagePreviewVO preview) {
  var simulation=preview.getSimulation();
  if(!preview.isCurrent() || !Objects.equals(command.getExpectedPageRevision(),preview.getPage().getDraftRevision())
    || !Objects.equals(command.getPageDigest(),preview.getPageDigest())
    || !Objects.equals(command.getInputDigest(),simulation.getInputDigest())
    || !Objects.equals(command.getFactDigest(),simulation.getFactDigest())) {
   throw new ArynBusinessException(409,"页面、模拟摘要或修订已变化，请重新预览确认");
  }
  var accepted=command.getAcceptedRiskCodes()==null?Set.<String>of():command.getAcceptedRiskCodes();
  for(var risk:simulation.getRisks()) {
   if(Set.of("STOCK_SHORT","GIFT_STOCK_UNPROVEN").contains(risk.getCode())) {throw new ArynBusinessException("当前库存不足或赠品库存不可核实，请修改方案后重新模拟");}
   if(!Set.of("CURRENT_FACT_ASSUMPTION","EXCLUDES_FREIGHT_POINTS").contains(risk.getCode()) && !accepted.contains(risk.getCode())) {
    throw new ArynBusinessException("请明确核定风险："+risk.getCode());
   }
  }
 }
}
