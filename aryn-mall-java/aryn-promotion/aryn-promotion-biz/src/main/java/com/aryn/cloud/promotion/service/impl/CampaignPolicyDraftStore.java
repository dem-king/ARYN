package com.aryn.cloud.promotion.service.impl;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.CampaignDraftSaveDTO;
import com.aryn.cloud.promotion.api.entity.*;
import com.aryn.cloud.promotion.mapper.CampaignDraftMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
/** 每次显式改政策生成独立未发布政策；旧资源保留，避免覆盖他人后续编辑。 */
@Service
@RequiredArgsConstructor
public class CampaignPolicyDraftStore {
	private final CampaignDraftStore drafts;
	private final CampaignDraftMapper mapper;
	private final PricePolicyService policies;
	private final CampaignResourceDigest digest;
	@Transactional(rollbackFor=Exception.class)
	public CampaignDraft save(String actor,String id,CampaignDraftSaveDTO definition,PricePolicy policy,List<PricePolicyItem> items) {
		var prior=drafts.owned(actor,id,true);
		if(!Objects.equals(prior.getRevision(),definition.getExpectedRevision()) || "PUBLISHED".equals(prior.getStatus())) {throw new ArynBusinessException(409,"活动包已变更");}
		var created=policies.save(ArynTenantContextHolder.getTenantId(),policy,items);
		var result=drafts.save(actor,id,definition,null,null,false);
		result.setMainPolicyId(created.getId());result.setMainPolicyDigest(digest.of(Arrays.asList(policies.get(created.getTenantId(),created.getId()),policies.listItems(created.getTenantId(),created.getId()))));result.setStatus("READY");
		mapper.update(null,Wrappers.lambdaUpdate(CampaignDraft.class).eq(CampaignDraft::getId,id).eq(CampaignDraft::getTenantId,created.getTenantId()).eq(CampaignDraft::getActorId,actor).eq(CampaignDraft::getRevision,result.getRevision())
			.set(CampaignDraft::getMainPolicyId,result.getMainPolicyId()).set(CampaignDraft::getMainPolicyDigest,result.getMainPolicyDigest()).set(CampaignDraft::getStatus,"READY"));
		return result;
	}
}
