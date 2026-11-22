package com.aryn.cloud.promotion.service.impl;
import com.alibaba.fastjson2.JSONObject;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.PageDesignDraftDTO;
import com.aryn.cloud.promotion.api.entity.*;
import com.aryn.cloud.promotion.mapper.CampaignDraftMapper;
import com.aryn.cloud.promotion.service.IPageDesignService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;
@Service
@RequiredArgsConstructor
public class CampaignPageDraftStore {
	private final CampaignDraftStore drafts;
	private final CampaignDraftMapper mapper;
	private final IPageDesignService pages;
	private final CampaignResourceDigest digest;
	@Transactional(rollbackFor=Exception.class)
	public void review(String actor,String id,Long revision,String pageId,Long pageRevision,String pageDigest) {
		var campaign=drafts.owned(actor,id,true);var page=pages.getById(pageId);
		if(!Objects.equals(campaign.getRevision(),revision)||!Objects.equals(campaign.getPageId(),pageId)||page==null
			||!Objects.equals(page.getDraftRevision(),pageRevision)||!Objects.equals(pageDigest,digest.of(JSONObject.parseObject(page.getPageContent())))){throw new ArynBusinessException(409,"页面或活动包已变化");}
		mapper.update(null,Wrappers.lambdaUpdate(CampaignDraft.class).eq(CampaignDraft::getId,id).eq(CampaignDraft::getTenantId,ArynTenantContextHolder.getTenantId()).eq(CampaignDraft::getActorId,actor).eq(CampaignDraft::getRevision,revision)
			.set(CampaignDraft::getPageRevision,pageRevision).set(CampaignDraft::getPageDigest,pageDigest));
	}

	@Transactional(rollbackFor=Exception.class)
	public CampaignDraft save(String actor,String id,Long revision,Long expectedPageRevision,JSONObject content) {
		var campaign=drafts.owned(actor,id,true);
		String pageName=campaign.getTitle().substring(0,Math.min(50,campaign.getTitle().length()));
		if(!Objects.equals(campaign.getRevision(),revision)||!"READY".equals(campaign.getStatus())){throw new ArynBusinessException(409,"活动包已变化");}
		PageDesign page;
		if(campaign.getPageId()==null){
			page=pages.createPage(new PageDesign().setPageName(pageName).setPageType("0").setHomeStatus("0").setStatus("0").setTenantId(ArynTenantContextHolder.getTenantId()).setCampaignDraftId(id).setDelFlag("0"));
		}else{page=pages.getById(campaign.getPageId());if(page==null||!id.equals(page.getCampaignDraftId())||!Objects.equals(expectedPageRevision,page.getDraftRevision())){throw new ArynBusinessException(409,"页面已变化，请重新加载；重新生成会覆盖当前页面草稿");}}
		var request=new PageDesignDraftDTO();request.setId(page.getId());request.setPageName(pageName);request.setPageContent(content);request.setSchemaVersion(3);request.setDraftRevision(page.getDraftRevision());
		long next=pages.saveDraft(request);
		mapper.update(null,Wrappers.lambdaUpdate(CampaignDraft.class).eq(CampaignDraft::getId,id).eq(CampaignDraft::getTenantId,ArynTenantContextHolder.getTenantId()).eq(CampaignDraft::getActorId,actor).eq(CampaignDraft::getRevision,revision)
			.set(CampaignDraft::getPageId,page.getId()).set(CampaignDraft::getPageRevision,next).set(CampaignDraft::getPageDigest,digest.of(content)));
		return drafts.owned(actor,id,false);
	}
}
