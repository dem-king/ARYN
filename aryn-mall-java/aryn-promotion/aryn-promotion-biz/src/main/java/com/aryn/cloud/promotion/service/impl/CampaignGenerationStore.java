package com.aryn.cloud.promotion.service.impl;

import cn.hutool.core.util.IdUtil;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.*;
import com.aryn.cloud.promotion.api.entity.CampaignGenerationJob;
import com.aryn.cloud.promotion.mapper.CampaignGenerationJobMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.*;

/** 短事务租约与草稿CAS；worker不持有活动发布权限，不导入采购域实体。 */
@Service
@RequiredArgsConstructor
public class CampaignGenerationStore {
	public static final String PROMPT_VERSION = "campaign-b2b-v1";
	private final CampaignGenerationJobMapper mapper;
	private final CampaignDraftStore drafts;
	private final ObjectMapper codec;

	@Transactional(rollbackFor = Exception.class)
	public CampaignGenerationJob enqueue(String actor, String draftId, Long expected, String requestId,
										  CampaignGenerationInput input, String digest, CampaignModelGateway.Metadata model) {
		if (!StringUtils.hasText(requestId) || requestId.length() > 64) { throw new ArynBusinessException("生成请求标识无效"); }
		var draft = drafts.owned(actor, draftId, true);
		var existing = mapper.selectOne(Wrappers.lambdaQuery(CampaignGenerationJob.class)
				.eq(CampaignGenerationJob::getTenantId, tenant()).eq(CampaignGenerationJob::getDraftId, draftId)
				.eq(CampaignGenerationJob::getRequestId, requestId));
		if (existing != null) {
			if (!actor.equals(existing.getActorId()) || !digest.equals(existing.getInputDigest())) {
				throw new ArynBusinessException(409, "请求标识已绑定其他生成输入");
			}
			return existing;
		}
		if (!Objects.equals(expected, draft.getRevision()) || "PUBLISHED".equals(draft.getStatus())) { conflict(); }
		long running = mapper.selectCount(Wrappers.lambdaQuery(CampaignGenerationJob.class)
				.eq(CampaignGenerationJob::getTenantId, tenant()).eq(CampaignGenerationJob::getDraftId, draftId)
				.and(q -> q.in(CampaignGenerationJob::getStatus, List.of("QUEUED", "RUNNING"))
					.or(f -> f.eq(CampaignGenerationJob::getStatus, "FAILED").isNotNull(CampaignGenerationJob::getNextRetryAt))));
		if (running > 0) { throw new ArynBusinessException(409, "活动包已有生成任务，可等待或取消"); }
		var job = new CampaignGenerationJob(); job.setId(IdUtil.getSnowflakeNextIdStr()); job.setTenantId(tenant());
		job.setDraftId(draftId); job.setActorId(actor); job.setBaseRevision(expected); job.setRequestId(requestId);
		job.setConfigVersion(model.configVersion()); job.setProviderId(model.provider()); job.setModel(model.model());
		job.setInputJson(encode(input)); job.setInputDigest(digest); job.setStatus("QUEUED"); job.setAttempt(0);
		job.setPromptVersion(PROMPT_VERSION); job.setCreateTime(LocalDateTime.now()); job.setUpdateTime(job.getCreateTime()); job.setDelFlag("0");
		mapper.insert(job); return job;
	}

	public CampaignGenerationJob find(String actor, String draftId, String requestId) {
		drafts.owned(actor, draftId, false);
		return mapper.selectOne(Wrappers.lambdaQuery(CampaignGenerationJob.class)
			.eq(CampaignGenerationJob::getTenantId, tenant()).eq(CampaignGenerationJob::getDraftId, draftId)
			.eq(CampaignGenerationJob::getActorId, actor).eq(CampaignGenerationJob::getRequestId, requestId));
	}
	public List<CampaignGenerationJob> list(String actor, String draftId) {
		drafts.owned(actor, draftId, false);
		return mapper.selectList(Wrappers.lambdaQuery(CampaignGenerationJob.class)
			.eq(CampaignGenerationJob::getTenantId, tenant()).eq(CampaignGenerationJob::getDraftId, draftId)
			.eq(CampaignGenerationJob::getActorId, actor).orderByDesc(CampaignGenerationJob::getCreateTime).last("LIMIT 20"));
	}
	@Transactional(rollbackFor = Exception.class)
	public void markStale(String id, String token) {
		var job = internal(id, true); requireLease(job, token); terminal(job, "STALE", null, "DRAFT_CHANGED");
	}
	@Transactional(rollbackFor = Exception.class)
	public void retry(String actor, String id) {
		var initial = get(actor, id);
		drafts.owned(actor, initial.getDraftId(), true);
		var job = internal(id, true);
		if (!Objects.equals(actor, job.getActorId()) || !"FAILED".equals(job.getStatus())) {
			throw new ArynBusinessException("仅本人的失败任务可重试");
		}
		if (!sourceCurrent(job)) { throw new ArynBusinessException(409, "草稿已变化，请重新生成"); }
		if (mapper.selectCount(Wrappers.lambdaQuery(CampaignGenerationJob.class)
			.eq(CampaignGenerationJob::getTenantId, tenant()).eq(CampaignGenerationJob::getDraftId, job.getDraftId())
			.ne(CampaignGenerationJob::getId, id).and(q -> q.in(CampaignGenerationJob::getStatus, List.of("QUEUED", "RUNNING"))
				.or(f -> f.eq(CampaignGenerationJob::getStatus, "FAILED").isNotNull(CampaignGenerationJob::getNextRetryAt)))) > 0) {
			throw new ArynBusinessException(409, "已有其他生成任务，请先取消");
		}
		mapper.update(null, Wrappers.lambdaUpdate(CampaignGenerationJob.class)
			.eq(CampaignGenerationJob::getTenantId, tenant()).eq(CampaignGenerationJob::getId, id)
			.set(CampaignGenerationJob::getStatus, "QUEUED").set(CampaignGenerationJob::getAttempt, 0)
			.set(CampaignGenerationJob::getNextRetryAt, null).set(CampaignGenerationJob::getErrorCode, null)
			.set(CampaignGenerationJob::getUpdateTime, LocalDateTime.now()));
	}

	public CampaignGenerationJob get(String actor, String id) {
		var job = internal(id, false);
		if (!Objects.equals(actor, job.getActorId())) { throw new ArynBusinessException("生成任务不存在"); }
		return job;
	}
	public CampaignGenerationJob internal(String id, boolean lock) {
		var query = Wrappers.lambdaQuery(CampaignGenerationJob.class).eq(CampaignGenerationJob::getTenantId, tenant()).eq(CampaignGenerationJob::getId, id);
		if (lock) { query.last("FOR UPDATE"); }
		var job = mapper.selectOne(query);
		if (job == null || !tenant().equals(job.getTenantId())) { throw new ArynBusinessException("生成任务不存在"); }
		return job;
	}
	public List<CampaignGenerationJob> due() {
		LocalDateTime now = LocalDateTime.now();
		return mapper.selectList(Wrappers.lambdaQuery(CampaignGenerationJob.class)
				.select(CampaignGenerationJob::getId).eq(CampaignGenerationJob::getTenantId, tenant())
				.and(q -> q.eq(CampaignGenerationJob::getStatus, "QUEUED")
					.or(f -> f.eq(CampaignGenerationJob::getStatus, "FAILED").le(CampaignGenerationJob::getNextRetryAt, now))
					.or(f -> f.eq(CampaignGenerationJob::getStatus, "RUNNING").lt(CampaignGenerationJob::getLeaseUntil, now)))
				.orderByAsc(CampaignGenerationJob::getCreateTime).last("LIMIT 8"));
	}
	@Transactional(rollbackFor = Exception.class)
	public String claim(String id) {
		LocalDateTime now = LocalDateTime.now(); String token = UUID.randomUUID().toString();
		var current = internal(id, true);
		if ("RUNNING".equals(current.getStatus()) && current.getLeaseUntil() != null
				&& current.getLeaseUntil().isBefore(now) && current.getAttempt() >= 3) {
			terminal(current, "FAILED", null, "LEASE_EXHAUSTED"); return null;
		}
		int changed = mapper.update(null, Wrappers.lambdaUpdate(CampaignGenerationJob.class)
				.eq(CampaignGenerationJob::getTenantId, tenant()).eq(CampaignGenerationJob::getId, id)
				.and(q -> q.eq(CampaignGenerationJob::getStatus, "QUEUED")
					.or(f -> f.eq(CampaignGenerationJob::getStatus, "FAILED").le(CampaignGenerationJob::getNextRetryAt, now))
					.or(f -> f.eq(CampaignGenerationJob::getStatus, "RUNNING").lt(CampaignGenerationJob::getLeaseUntil, now)))
				.set(CampaignGenerationJob::getStatus, "RUNNING").set(CampaignGenerationJob::getLeaseToken, token)
				.set(CampaignGenerationJob::getLeaseUntil, now.plusMinutes(5)).set(CampaignGenerationJob::getNextRetryAt, null)
				.set(CampaignGenerationJob::getErrorCode, null).set(CampaignGenerationJob::getUpdateTime, now)
				.setSql("attempt=attempt+1"));
		return changed == 1 ? token : null;
	}
	@Transactional(rollbackFor = Exception.class)
	public void bind(String id, String token, CampaignModelGateway.Metadata model) {
		var job = internal(id, true); requireLease(job, token);
		if (job.getConfigVersion() != null && !job.getConfigVersion().equals(model.configVersion())) {
			throw new ArynBusinessException(409, "生成任务配置版本不可替换");
		}
		mapper.update(null, Wrappers.lambdaUpdate(CampaignGenerationJob.class)
				.eq(CampaignGenerationJob::getTenantId, tenant()).eq(CampaignGenerationJob::getId, id)
				.set(CampaignGenerationJob::getConfigVersion, model.configVersion()).set(CampaignGenerationJob::getProviderId, model.provider())
				.set(CampaignGenerationJob::getModel, model.model()).set(CampaignGenerationJob::getUpdateTime, LocalDateTime.now()));
	}

	@Transactional(rollbackFor = Exception.class)
	public void complete(String id, String token, CampaignGenerationParser.Proposal proposal, String auditedOutput) {
		var unlocked = internal(id, false);
		// 与人工保存/排队保持先锁活动包的顺序，避免循环等待。
		var draft = drafts.owned(unlocked.getActorId(), unlocked.getDraftId(), true);
		var job = internal(id, true); requireLease(job, token);
		if (!Objects.equals(draft.getRevision(), job.getBaseRevision()) || "PUBLISHED".equals(draft.getStatus())) {
			terminal(job, "STALE", null, "DRAFT_CHANGED"); return;
		}
		CampaignDraftSaveDTO definition = drafts.decode(draft.getDefinitionJson());
		definition.setExpectedRevision(job.getBaseRevision()); definition.setActivity(proposal.activity()); definition.setCoupon(proposal.coupon());
		drafts.save(job.getActorId(), job.getDraftId(), definition, proposal.activity(), proposal.reason(), true);
		terminal(job, "SUCCEEDED", auditedOutput, null);
	}

	@Transactional(rollbackFor = Exception.class)
	public void fail(String id, String token, String code, boolean retryable) {
		var job = internal(id, true);
		if (!validLease(job, token)) { return; }
		LocalDateTime retry = retryable && job.getAttempt() < 3 ? LocalDateTime.now().plusSeconds(30L * job.getAttempt()) : null;
		mapper.update(null, Wrappers.lambdaUpdate(CampaignGenerationJob.class).eq(CampaignGenerationJob::getTenantId, tenant())
				.eq(CampaignGenerationJob::getId, id).set(CampaignGenerationJob::getStatus, "FAILED")
				.set(CampaignGenerationJob::getErrorCode, code).set(CampaignGenerationJob::getNextRetryAt, retry)
				.set(CampaignGenerationJob::getLeaseToken, null).set(CampaignGenerationJob::getLeaseUntil, null)
				.set(CampaignGenerationJob::getUpdateTime, LocalDateTime.now()));
	}
	@Transactional(rollbackFor = Exception.class)
	public void cancel(String actor, String id) {
		var job = internal(id, true);
		if (!Objects.equals(actor, job.getActorId())) { throw new ArynBusinessException("生成任务不存在"); }
		if (List.of("SUCCEEDED", "STALE").contains(job.getStatus())) { throw new ArynBusinessException("已完成生成不能取消，请编辑活动包"); }
		terminal(job, "CANCELLED", null, null);
	}
	public boolean sourceCurrent(CampaignGenerationJob job) {
		var draft = drafts.owned(job.getActorId(), job.getDraftId(), false);
		return Objects.equals(draft.getRevision(), job.getBaseRevision()) && !"PUBLISHED".equals(draft.getStatus());
	}
	public CampaignGenerationInput input(CampaignGenerationJob job) {
		try { return codec.readValue(job.getInputJson(), CampaignGenerationInput.class); }
		catch (Exception invalid) { throw new ArynBusinessException("生成输入无法读取"); }
	}
	private void terminal(CampaignGenerationJob job, String status, String output, String error) {
		mapper.update(null, Wrappers.lambdaUpdate(CampaignGenerationJob.class).eq(CampaignGenerationJob::getTenantId, tenant())
				.eq(CampaignGenerationJob::getId, job.getId()).set(CampaignGenerationJob::getStatus, status)
				.set(CampaignGenerationJob::getOutputJson, output).set(CampaignGenerationJob::getErrorCode, error)
				.set(CampaignGenerationJob::getNextRetryAt, null).set(CampaignGenerationJob::getLeaseToken, null)
				.set(CampaignGenerationJob::getLeaseUntil, null).set(CampaignGenerationJob::getUpdateTime, LocalDateTime.now()));
	}
	private boolean validLease(CampaignGenerationJob job, String token) {
		return "RUNNING".equals(job.getStatus()) && Objects.equals(token, job.getLeaseToken())
				&& job.getLeaseUntil() != null && job.getLeaseUntil().isAfter(LocalDateTime.now());
	}
	private void requireLease(CampaignGenerationJob job, String token) {
		if (!validLease(job, token)) { throw new ArynBusinessException(409, "生成租约已过期或任务已取消"); }
	}
	private String encode(Object value) { return drafts.encode(value); }
	private String tenant() {
		String tenant = ArynTenantContextHolder.getTenantId();
		if (!StringUtils.hasText(tenant)) { throw new ArynBusinessException("缺少租户上下文"); } return tenant;
	}
	private void conflict() { throw new ArynBusinessException(409, "活动包已变更或发布，请重新加载"); }
}
