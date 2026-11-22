package com.aryn.cloud.promotion.service.impl;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.*;
import com.aryn.cloud.promotion.api.entity.*;
import com.aryn.cloud.promotion.api.vo.CampaignDraftVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

/** 协议价沿既有价格政策领域写草稿，模型生成接口不接受POLICY类型。 */
@Service
@RequiredArgsConstructor
public class CampaignPolicyDraftService {
	private final CampaignDraftStore drafts;
	private final CampaignDraftService views;
	private final CampaignProposalValidator validator;
	private final CampaignPolicyDraftStore store;
	public CampaignDraftVO save(String actor,String id,CampaignPolicyCommandDTO command) {
		var draft=drafts.owned(actor,id,false);var definition=drafts.decode(draft.getDefinitionJson());
		if(command==null || !Objects.equals(command.getExpectedRevision(),draft.getRevision())) {throw new ArynBusinessException(409,"活动包已变化");}
		var input=validator.prepare(definition);validator.validateCoupon(definition,definition.getCoupon());
		if(!List.of("2","3").contains(definition.getPurchaseScene())) {throw new ArynBusinessException("协议政策必须明确船舶或企业主体");}
		if(!StringUtils.hasText(command.getName()) || command.getName().length()>64 || command.getItems()==null || command.getItems().isEmpty() || command.getItems().size()>100) {throw new ArynBusinessException("协议政策名称或SKU明细无效");}
		var byId=input.getCandidates().stream().collect(Collectors.toMap(CampaignGenerationInput.Candidate::getSkuId,c->c));
		Set<String> ids=new HashSet<>();List<PricePolicyItem> items=new ArrayList<>();
		for(var row:command.getItems()) {
			var candidate=row==null?null:byId.get(row.getSkuId());
			if(candidate==null || !ids.add(row.getSkuId()) || row.getMinQty()==null || row.getMinQty()<1 || row.getPrice()==null
				|| row.getPrice().scale()>2 || row.getPrice().signum()<=0 || row.getPrice().compareTo(candidate.getSalesPrice())>=0
				|| candidate.getSalesPrice().subtract(row.getPrice()).multiply(new BigDecimal("100")).compareTo(candidate.getSalesPrice().multiply(policyDiscountLimit(definition)))>0) {
				throw new ArynBusinessException("协议价必须引用核实SKU，且满足起订量和让利上限");
			}
			items.add(new PricePolicyItem().setSkuId(row.getSkuId()).setPolicyPrice(row.getPrice()).setMinQty(row.getMinQty()));
		}
		var policy=new PricePolicy().setPolicyName(command.getName()).setPriceMode(PricePolicy.MODE_FIXED_PRICE).setStatus(PricePolicy.STATUS_DRAFT)
			.setPriority(command.getPriority()==null?0:command.getPriority()).setStartTime(definition.getStartTime()).setEndTime(definition.getEndTime())
			.setApplyType("2".equals(definition.getPurchaseScene())?PricePolicy.APPLY_VESSEL:PricePolicy.APPLY_BUYER_ORG)
			.setApplyValue("2".equals(definition.getPurchaseScene())?definition.getVesselId():definition.getBuyerOrgId()).setTenantId(ArynTenantContextHolder.getTenantId()).setDelFlag("0");
		definition.setExpectedRevision(command.getExpectedRevision());definition.setPrimaryType("POLICY");definition.setActivity(null);definition.setPolicy(command);
		return views.view(store.save(actor,id,definition,policy,items));
	}
 private BigDecimal policyDiscountLimit(CampaignDraftSaveDTO definition){
  if(definition.getCoupon()==null){return definition.getMaxDiscountPercent();}
  var coupon=definition.getCoupon();
  var reserved=coupon.getAmount().multiply(new BigDecimal("100")).divide(coupon.getThreshold(),8,java.math.RoundingMode.CEILING);
  return definition.getMaxDiscountPercent().subtract(reserved);
 }

}
