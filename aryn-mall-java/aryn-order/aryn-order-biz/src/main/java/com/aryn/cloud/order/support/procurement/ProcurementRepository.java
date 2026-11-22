package com.aryn.cloud.order.support.procurement;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.ProcurementCommands.BatchSubmission;
import com.aryn.cloud.order.api.entity.*;
import com.aryn.cloud.order.mapper.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** 本地事务边界；RPC/模型/文件抽取均不在这些方法内执行。 */
@Repository
@RequiredArgsConstructor
public class ProcurementRepository {
    private final ProcurementDraftMapper drafts;
    private final ProcurementSourceBatchMapper batches;
    private final ProcurementDraftRowMapper rows;
    private final ProcurementContributionMapper contributions;

    private String tenant() {
        String tenant = ArynTenantContextHolder.getTenantId();
        if (tenant == null || tenant.isBlank()) throw new ArynBusinessException(403, "缺少租户上下文");
        return tenant;
    }
    public ProcurementDraft draft(String id, boolean lock) {
        var query = Wrappers.<ProcurementDraft>lambdaQuery().eq(ProcurementDraft::getTenantId, tenant())
            .eq(ProcurementDraft::getId, id);
        if (lock) query.last("FOR UPDATE");
        return drafts.selectOne(query);
    }
    public List<ProcurementDraft> list(String actor) {
        return drafts.selectList(Wrappers.<ProcurementDraft>lambdaQuery().eq(ProcurementDraft::getTenantId, tenant())
            .eq(ProcurementDraft::getOwnerUserId, actor).orderByDesc(ProcurementDraft::getUpdateTime)
            .orderByDesc(ProcurementDraft::getId).last("LIMIT 30"));
    }
    @Transactional(rollbackFor = Exception.class)
    public ProcurementDraft create(ProcurementDraft draft) {
        try { drafts.insert(draft); return draft; }
        catch (DuplicateKeyException duplicate) {
            var existing = drafts.selectOne(Wrappers.<ProcurementDraft>lambdaQuery()
                .eq(ProcurementDraft::getTenantId, tenant()).eq(ProcurementDraft::getOwnerUserId, draft.getOwnerUserId())
                .eq(ProcurementDraft::getClientRequestId, draft.getClientRequestId()).last("FOR UPDATE"));
            if (existing == null || !Objects.equals(existing.getContextFingerprint(), draft.getContextFingerprint()))
                throw new ArynBusinessException(409, "请求标识已用于不同的采购，请重新创建请求");
            return existing;
        }
    }
    public ProcurementSourceBatch batch(String id) {
        return batches.selectOne(Wrappers.<ProcurementSourceBatch>lambdaQuery().eq(ProcurementSourceBatch::getTenantId, tenant())
            .eq(ProcurementSourceBatch::getId, id));
    }
    public List<ProcurementSourceBatch> batches(String draftId) {
        // 原件按单批取出，列表不加载BLOB和全文。
        return batches.selectList(Wrappers.<ProcurementSourceBatch>lambdaQuery()
            .select(ProcurementSourceBatch.class, field -> !Set.of("inputBytes", "inputText", "leaseToken").contains(field.getProperty()))
            .eq(ProcurementSourceBatch::getTenantId, tenant()).eq(ProcurementSourceBatch::getDraftId, draftId)
            .orderByDesc(ProcurementSourceBatch::getCreateTime).orderByDesc(ProcurementSourceBatch::getId).last("LIMIT 100"));
    }
    public List<ProcurementDraftRow> rows(String batchId, String after) {
        return rows.selectList(Wrappers.<ProcurementDraftRow>lambdaQuery().eq(ProcurementDraftRow::getTenantId, tenant())
            .eq(ProcurementDraftRow::getBatchId, batchId).gt(after != null, ProcurementDraftRow::getSourcePosition, after)
            .orderByAsc(ProcurementDraftRow::getSourcePosition).last("LIMIT 100"));
    }
    @Transactional(rollbackFor = Exception.class)
    public BatchSubmission submit(ProcurementSourceBatch input, long expectedRevision) {
        var draft = draft(input.getDraftId(), true);
        if (draft == null || !Objects.equals(draft.getOwnerUserId(), input.getOperatorUserId()))
            throw new ArynBusinessException(403, "无权访问该采购草稿");
        var same = batches.selectOne(Wrappers.<ProcurementSourceBatch>lambdaQuery()
            .eq(ProcurementSourceBatch::getTenantId, tenant()).eq(ProcurementSourceBatch::getDraftId, draft.getId())
            .eq(ProcurementSourceBatch::getOperatorUserId, input.getOperatorUserId())
            .eq(ProcurementSourceBatch::getClientRequestId, input.getClientRequestId()));
        if (same != null) {
            if (!Objects.equals(same.getInputDigest(), input.getInputDigest())
                    || !Objects.equals(same.getInputType(), input.getInputType())
                    || !Objects.equals(same.getExplicitNew(), input.getExplicitNew()))
                throw new ArynBusinessException(409, "请求标识已用于不同的输入");
            return new BatchSubmission("REPLAY", same.getId(), draft.getRevision());
        }
        if (!"OPEN".equals(draft.getStatus())) throw new ArynBusinessException(409, "采购草稿当前不可追加来源");
        if (!Boolean.TRUE.equals(input.getExplicitNew())) {
            var duplicate = batches.selectOne(Wrappers.<ProcurementSourceBatch>lambdaQuery()
                .eq(ProcurementSourceBatch::getTenantId, tenant()).eq(ProcurementSourceBatch::getDraftId, draft.getId())
                .eq(ProcurementSourceBatch::getOperatorUserId, input.getOperatorUserId())
                .eq(ProcurementSourceBatch::getInputDigest, input.getInputDigest())
                .eq(ProcurementSourceBatch::getInputType, input.getInputType())
                .ne(ProcurementSourceBatch::getStatus, "CANCELLED").orderByDesc(ProcurementSourceBatch::getCreateTime).last("LIMIT 1"));
            if (duplicate != null) return new BatchSubmission("DUPLICATE", duplicate.getId(), draft.getRevision());
        }
        if (!Objects.equals(draft.getRevision(), expectedRevision))
            throw new ArynBusinessException(409, "草稿修订冲突，请重新加载");
        batches.insert(input);
        draft.setRevision(draft.getRevision() + 1);
        draft.setUpdateTime(LocalDateTime.now()); draft.setUpdateBy(input.getOperatorUserId());
        drafts.updateById(draft);
        return new BatchSubmission("CREATED", input.getId(), draft.getRevision());
    }
}
