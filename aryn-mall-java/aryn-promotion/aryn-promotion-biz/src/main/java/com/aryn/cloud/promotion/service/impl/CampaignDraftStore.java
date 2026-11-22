package com.aryn.cloud.promotion.service.impl;

import cn.hutool.core.util.IdUtil;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.*;
import com.aryn.cloud.promotion.api.entity.CampaignDraft;
import com.aryn.cloud.promotion.mapper.CampaignDraftMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;

/** 活动包写事务只访问本promotion库和既有领域命令；模型/商品RPC在外层完成。 */
@Service
@RequiredArgsConstructor
public class CampaignDraftStore {
	private final CampaignDraftMapper mapper;
	private final B2bActivityCommandService commands;
	private final ObjectMapper json;

	public CampaignDraft owned(String actor, String id, boolean lock) {
		if (!StringUtils.hasText(actor)) { throw new ArynBusinessException("缺少活动包操作者"); }
		var query = Wrappers.lambdaQuery(CampaignDraft.class).eq(CampaignDraft::getId, id)
				.eq(CampaignDraft::getTenantId, tenant()).eq(CampaignDraft::getActorId, actor);
		if (lock) { query.last("FOR UPDATE"); }
		var draft = mapper.selectOne(query);
		if (draft == null || !tenant().equals(draft.getTenantId()) || !actor.equals(draft.getActorId())) {
			throw new ArynBusinessException("活动包不存在");
		}
		return draft;
	}

	public List<CampaignDraft> list(String actor) {
		if (!StringUtils.hasText(actor)) { throw new ArynBusinessException("缺少操作者"); }
		return mapper.selectList(Wrappers.lambdaQuery(CampaignDraft.class).eq(CampaignDraft::getTenantId, tenant())
				.eq(CampaignDraft::getActorId, actor).orderByDesc(CampaignDraft::getUpdateTime).last("LIMIT 50"));
	}

	@Transactional(rollbackFor = Exception.class)
	public CampaignDraft save(String actor, String id, CampaignDraftSaveDTO definition,
							  B2bActivitySaveDTO normalized, String reason, boolean generated) {
		CampaignDraft draft;
		if (id == null) {
			if (!StringUtils.hasText(actor)) { throw new ArynBusinessException("缺少操作者"); }
			draft = new CampaignDraft(); draft.setId(IdUtil.getSnowflakeNextIdStr()); draft.setTenantId(tenant());
			draft.setActorId(actor); draft.setRevision(0L); draft.setCreateTime(LocalDateTime.now());
			draft.setGenerationVersion(0); draft.setDelFlag("0");
		}
		else {
			draft = owned(actor, id, true);
			if (!Objects.equals(definition.getExpectedRevision(), draft.getRevision())) { conflict(); }
			if ("PUBLISHED".equals(draft.getStatus())) { throw new ArynBusinessException("已发布活动包请复制为新草稿"); }
			draft.setRevision(draft.getRevision() + 1);
		}
		if (normalized != null) {
			var prior = draft.getMainActivityId() == null ? null : commands.get(draft.getMainActivityId());
			com.aryn.cloud.promotion.api.entity.PromotionActivity activity;
			if (prior == null || !prior.getActivityType().equals(normalized.getActivityType())) {
				activity = commands.create(normalized);
			}
			else {
				normalized.setExpectedRevision(draft.getMainActivityRevision());
				activity = commands.update(prior.getId(), normalized);
			}
			draft.setMainActivityId(activity.getId()); draft.setMainActivityRevision(activity.getRevision());
			normalized.setExpectedRevision(null); definition.setActivity(normalized);
		}
		else {
			draft.setMainActivityId(null); draft.setMainActivityRevision(null); definition.setActivity(null);
		}
		if (generated) { draft.setGenerationVersion(draft.getGenerationVersion() + 1); }
		draft.setMainPolicyId(null); draft.setMainPolicyDigest(null);
		draft.setGenerationReason(reason);
		draft.setSimulationId(null); draft.setSimulationRevision(null); draft.setPageDigest(null);
		draft.setTitle(StringUtils.hasText(definition.getTitle()) ? definition.getTitle() : "待完善活动包");
		draft.setStatus(normalized == null ? "DRAFT" : "READY");
		draft.setUpdateTime(LocalDateTime.now());
		CampaignDraftSaveDTO stored = decode(encode(definition)); stored.setExpectedRevision(null);
		draft.setDefinitionJson(encode(stored));
		if (id == null) { mapper.insert(draft); }
		else {
			CampaignDraft patch = new CampaignDraft(); org.springframework.beans.BeanUtils.copyProperties(draft, patch);
			// MP默认跳过NULL，显式清理失效凭据和被人工移除的主活动。
			patch.setMainPolicyId(null); patch.setMainPolicyDigest(null);
			patch.setMainActivityId(null); patch.setMainActivityRevision(null); patch.setSimulationId(null);
			patch.setSimulationRevision(null); patch.setPageDigest(null); patch.setGenerationReason(null);
			int changed = mapper.update(patch, Wrappers.lambdaUpdate(CampaignDraft.class)
					.eq(CampaignDraft::getTenantId, tenant()).eq(CampaignDraft::getActorId, actor).eq(CampaignDraft::getId, id)
					.eq(CampaignDraft::getRevision, definition.getExpectedRevision())
					.set(CampaignDraft::getMainPolicyId, null).set(CampaignDraft::getMainPolicyDigest, null)
					.set(CampaignDraft::getMainActivityId, draft.getMainActivityId())
					.set(CampaignDraft::getMainActivityRevision, draft.getMainActivityRevision())
					.set(CampaignDraft::getSimulationId, null).set(CampaignDraft::getSimulationRevision, null)
					.set(CampaignDraft::getPageDigest, null).set(CampaignDraft::getGenerationReason, reason));
			if (changed != 1) { conflict(); }
		}
		return draft;
	}

	@Transactional(rollbackFor = Exception.class)
	public void bindSimulation(String actor, String id, Long revision, String simulationId) {
		var draft = owned(actor, id, true);
		if (!Objects.equals(draft.getRevision(), revision) || !"READY".equals(draft.getStatus())) { conflict(); }
		int changed = mapper.update(null, Wrappers.lambdaUpdate(CampaignDraft.class)
			.eq(CampaignDraft::getTenantId, tenant()).eq(CampaignDraft::getId, id).eq(CampaignDraft::getActorId, actor)
			.eq(CampaignDraft::getRevision, revision).set(CampaignDraft::getSimulationId, simulationId)
			.set(CampaignDraft::getSimulationRevision, revision));
		if (changed != 1) { conflict(); }
	}

	public CampaignDraftSaveDTO decode(String value) {
		try { return json.readValue(value, CampaignDraftSaveDTO.class); }
		catch (Exception invalid) { throw new ArynBusinessException("活动包数据格式非法"); }
	}
	public String encode(Object value) {
		try { return json.writeValueAsString(value); }
		catch (Exception invalid) { throw new IllegalStateException("活动包无法序列化", invalid); }
	}
	private String tenant() {
		String tenant = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenant)) { throw new ArynBusinessException("缺少租户上下文"); }
		return tenant;
	}
	private void conflict() { throw new ArynBusinessException(409, "活动包已变更，请刷新后重试"); }
}
