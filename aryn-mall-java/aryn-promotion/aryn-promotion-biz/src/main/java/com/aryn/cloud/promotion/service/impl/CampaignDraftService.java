package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.*;
import com.aryn.cloud.promotion.api.entity.CampaignDraft;
import com.aryn.cloud.promotion.api.vo.CampaignDraftVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.util.List;

/** RPC准备与短事务持久化分开；人工保存和模型完成都使用同一约束和领域草稿命令。 */
@Service
@RequiredArgsConstructor
public class CampaignDraftService {
	private final CampaignDraftStore store;
	private final CampaignProposalValidator validator;
	private final PricePolicyService policies;

	public CampaignDraftVO get(String actor, String id) { return view(store.owned(actor, id, false)); }
	public List<CampaignDraftVO> list(String actor) { return store.list(actor).stream().map(this::view).toList(); }

	public CampaignDraftVO save(String actor, String id, CampaignDraftSaveDTO request) {
		if (id != null) { store.owned(actor, id, false); }
		validator.validateMetadata(request);
		if(request.getPolicy()!=null || "POLICY".equals(request.getPrimaryType())) {throw new ArynBusinessException("协议政策请使用显式政策草稿命令");}
		CampaignDraftSaveDTO definition = store.decode(store.encode(request));
		B2bActivitySaveDTO normalized = null;
		if (definition.getActivity() != null || definition.getCoupon() != null) {
			var facts = validator.prepare(definition);
			if (definition.getActivity() != null) { normalized = validator.normalize(facts, definition.getActivity()); }
			validator.validateCoupon(definition, definition.getCoupon());
		}
		// 协议价是独立领域：这里只关联既有政策，不把type=8伪装成统一活动写入。
		if (StringUtils.hasText(definition.getReferencedPolicyId())
				&& policies.get(ArynTenantContextHolder.getTenantId(), definition.getReferencedPolicyId()) == null) {
			throw new ArynBusinessException("关联价格政策不存在");
		}
		return view(store.save(actor, id, definition, normalized, null, false));
	}

	public CampaignDraftVO view(CampaignDraft draft) {
		var vo = new CampaignDraftVO(); BeanUtils.copyProperties(draft, vo);
		var definition = store.decode(draft.getDefinitionJson()); definition.setExpectedRevision(draft.getRevision());
		vo.setDefinition(definition); vo.setMissingFields(validator.missing(definition));
		return vo;
	}
}
