package com.aryn.cloud.promotion.service.impl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.CampaignPageCommandDTO;
import com.aryn.cloud.promotion.api.vo.*;
import com.aryn.cloud.promotion.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.Objects;
@Service
@RequiredArgsConstructor
public class CampaignPageDraftService {
	private final CampaignDraftService drafts;
	private final CampaignPageDraftStore store;
	private final CampaignProposalValidator validator;
	private final CampaignPageDraftBuilder builder;
	private final PageDesignDocumentValidator documents;
	private final IPageDesignService pages;
	private final CampaignSimulationService simulations;
	private final CampaignResourceDigest digest;
	public CampaignDraftVO generate(String actor,String id,CampaignPageCommandDTO command){
		var campaign=drafts.get(actor,id);if(!Objects.equals(campaign.getRevision(),command.getExpectedRevision())){throw new ArynBusinessException(409,"活动包已变化");}
		var facts=validator.prepare(campaign.getDefinition());var content=builder.build(id,campaign.getRevision(),facts);builder.validate(content,facts);
		var errors=documents.validate(content.toJSONString(),"0");if(!errors.isEmpty()){throw new ArynBusinessException("页面草稿校验失败："+errors);}
		return drafts.view(store.save(actor,id,command.getExpectedRevision(),command.getExpectedPageRevision(),content));
	}
	public CampaignPagePreviewVO review(String actor,String id,CampaignPageCommandDTO command){
		var preview=preview(actor,id,command);
		if(!Objects.equals(command.getExpectedPageRevision(),preview.getPage().getDraftRevision())){throw new ArynBusinessException(409,"页面修订已变化");}
		store.review(actor,id,command.getExpectedRevision(),preview.getPage().getId(),preview.getPage().getDraftRevision(),preview.getPageDigest());
		preview.setCurrent(true);return preview;
	}

	public CampaignPagePreviewVO preview(String actor,String id,CampaignPageCommandDTO command){
		var campaign=drafts.get(actor,id);if(!Objects.equals(campaign.getRevision(),command.getExpectedRevision())||campaign.getPageId()==null){throw new ArynBusinessException(409,"活动包或页面已变化");}
		var page=pages.getEditor(campaign.getPageId());
		if(!"0".equals(page.getPageType())){throw new ArynBusinessException("活动包页面须保持微页面类型");}var facts=validator.prepare(campaign.getDefinition());builder.validate(page.getPageContent(),facts);
		var errors=documents.validate(page.getPageContent().toJSONString(),"0");if(!errors.isEmpty()){throw new ArynBusinessException("页面校验失败："+errors);}
		var simulation=simulations.requireCurrent(actor,command.getSimulationId());
		if(!id.equals(simulation.getCampaignDraftId())||!Objects.equals(campaign.getRevision(),simulation.getExpectedCampaignRevision())){throw new ArynBusinessException("模拟结果不属于当前活动包");}
		var result=new CampaignPagePreviewVO();result.setCampaignDraftId(id);result.setCampaignRevision(campaign.getRevision());result.setPage(page);result.setSimulation(simulation);result.setPageDigest(digest.of(page.getPageContent()));
		var sampleLines=simulation.getOrderComparison()==null?java.util.List.<CampaignOrderComparisonVO.Line>of():simulation.getOrderComparison().getProposed().getLines();
		result.setGoods(facts.getCandidates().stream().filter(c->sampleLines.stream().anyMatch(line->c.getSkuId().equals(line.getSkuId()))).map(c->{
			var goods=new CampaignPagePreviewVO.Goods();goods.setId(c.getSpuId());goods.setSkuId(c.getSkuId());goods.setName(c.getName());goods.setStock(c.getStock());goods.setOriginalPrice(c.getSalesPrice());
			goods.setPrice(sampleLines.stream().filter(line->c.getSkuId().equals(line.getSkuId())).map(CampaignOrderComparisonVO.Line::getUnitPrice).filter(java.util.Objects::nonNull).min(java.math.BigDecimal::compareTo).orElse(null));return goods;
		}).filter(goods->goods.getPrice()!=null).toList());result.setCurrent(Objects.equals(campaign.getPageRevision(),page.getDraftRevision())&&Objects.equals(campaign.getPageDigest(),result.getPageDigest()));return result;
	}
}
