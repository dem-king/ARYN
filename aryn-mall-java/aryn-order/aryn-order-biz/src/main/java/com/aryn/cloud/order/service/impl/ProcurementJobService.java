package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.*;
import com.aryn.cloud.order.mapper.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** 持久任务的短事务；租约令牌用于拒绝过期worker提交。 */
@Service
@RequiredArgsConstructor
public class ProcurementJobService {
    private final ProcurementSourceBatchMapper batches;
    private final ProcurementDraftRowMapper rows;
    private String tenant() {
        String tenant = ArynTenantContextHolder.getTenantId();
        if (tenant == null || tenant.isBlank()) throw new ArynBusinessException(403, "缺少租户上下文");
        return tenant;
    }
    public List<ProcurementSourceBatch> due() {
        var now = LocalDateTime.now();
        return batches.selectList(Wrappers.<ProcurementSourceBatch>lambdaQuery()
            .select(ProcurementSourceBatch::getId, ProcurementSourceBatch::getTenantId)
            .eq(ProcurementSourceBatch::getTenantId, tenant())
            .and(q -> q.eq(ProcurementSourceBatch::getStatus, "QUEUED")
                .or(f -> f.eq(ProcurementSourceBatch::getStatus, "FAILED").le(ProcurementSourceBatch::getNextRetryAt, now))
                .or(r -> r.eq(ProcurementSourceBatch::getStatus, "RUNNING").lt(ProcurementSourceBatch::getLeaseUntil, now)))
            .orderByAsc(ProcurementSourceBatch::getCreateTime).last("LIMIT 8"));
    }
    @Transactional(rollbackFor = Exception.class)
    public String claim(String id) {
        var now = LocalDateTime.now(); var token = UUID.randomUUID().toString();
        int changed = batches.update(null, Wrappers.<ProcurementSourceBatch>lambdaUpdate()
            .eq(ProcurementSourceBatch::getTenantId, tenant()).eq(ProcurementSourceBatch::getId, id)
            .and(q -> q.eq(ProcurementSourceBatch::getStatus, "QUEUED")
                .or(f -> f.eq(ProcurementSourceBatch::getStatus, "FAILED").le(ProcurementSourceBatch::getNextRetryAt, now))
                .or(r -> r.eq(ProcurementSourceBatch::getStatus, "RUNNING").lt(ProcurementSourceBatch::getLeaseUntil, now)))
            .set(ProcurementSourceBatch::getStatus, "RUNNING").set(ProcurementSourceBatch::getLeaseToken, token)
            .set(ProcurementSourceBatch::getLeaseUntil, now.plusMinutes(5)).set(ProcurementSourceBatch::getHeartbeat, now)
            .set(ProcurementSourceBatch::getNextRetryAt, null).set(ProcurementSourceBatch::getUpdateTime, now)
            .setSql("attempt = attempt + 1, revision = revision + 1"));
        return changed == 1 ? token : null;
    }
    private ProcurementSourceBatch lock(String id) {
        return batches.selectOne(Wrappers.<ProcurementSourceBatch>lambdaQuery()
            .select(ProcurementSourceBatch.class, field -> !Set.of("inputBytes", "inputText").contains(field.getProperty()))
            .eq(ProcurementSourceBatch::getTenantId, tenant()).eq(ProcurementSourceBatch::getId, id).last("FOR UPDATE"));
    }
    private void requireLease(ProcurementSourceBatch batch, String token) {
        if (batch == null || !"RUNNING".equals(batch.getStatus()) || !Objects.equals(batch.getLeaseToken(), token)
                || batch.getLeaseUntil() == null || !batch.getLeaseUntil().isAfter(LocalDateTime.now()))
            throw new ArynBusinessException(409, "采购任务已取消或租约失效");
    }
    /** 外呼前检查；更新心跳也使用短事务，完成后释放连接。 */
    @Transactional(rollbackFor = Exception.class)
    public void heartbeat(String id, String token) {
        var batch = lock(id); requireLease(batch, token);
        batch.setHeartbeat(LocalDateTime.now()); batch.setLeaseUntil(batch.getHeartbeat().plusMinutes(5));
        batches.updateById(batch);
    }
    @Transactional(rollbackFor = Exception.class)
    public void saveChunk(String id, String token, List<ProcurementDraftRow> chunk, int nextCursor, int total) {
        var batch = lock(id); requireLease(batch, token);
        if (chunk.size() > 32 || nextCursor > total || nextCursor < batch.getSourceCursor())
            throw new IllegalArgumentException("非法采购分片进度");
        if (batch.getSourceCursor() == nextCursor) return;
        if (nextCursor - batch.getSourceCursor() != chunk.size()) throw new IllegalArgumentException("采购分片覆盖不完整");
        for (var row : chunk) {
            if (!Objects.equals(row.getBatchId(), id) || !Objects.equals(row.getDraftId(), batch.getDraftId())
                    || !Objects.equals(row.getTenantId(), tenant()) || !Objects.equals(row.getParseRevision(), batch.getParseRevision()))
                throw new IllegalArgumentException("非法采购行归属");
            rows.insert(row);
        }
        batch.setSourceCursor(nextCursor); batch.setCompletedRows(nextCursor); batch.setTotalRows(total);
        batch.setHeartbeat(LocalDateTime.now()); batch.setLeaseUntil(batch.getHeartbeat().plusMinutes(5));
        batch.setRevision(batch.getRevision() + 1); batch.setUpdateTime(batch.getHeartbeat()); batches.updateById(batch);
    }
    @Transactional(rollbackFor = Exception.class)
    public void finish(String id, String token, int total) {
        var batch = lock(id); requireLease(batch, token);
        if (total <= 0 || batch.getCompletedRows() != total) throw new IllegalStateException("采购输入尚未完整覆盖");
        long pending = rows.selectCount(Wrappers.<ProcurementDraftRow>lambdaQuery()
            .eq(ProcurementDraftRow::getTenantId, tenant()).eq(ProcurementDraftRow::getBatchId, id)
            .notIn(ProcurementDraftRow::getReviewStatus, List.of("CONFIRMED", "REJECTED")));
        batch.setStatus(pending == 0 ? "READY" : "REVIEW_REQUIRED");
        batch.setLeaseToken(null); batch.setLeaseUntil(null); batch.setRevision(batch.getRevision() + 1);
        batch.setUpdateTime(LocalDateTime.now());
        // updateById不写NULL，显式清理租约。
        batches.update(batch, Wrappers.<ProcurementSourceBatch>lambdaUpdate().eq(ProcurementSourceBatch::getTenantId, tenant())
            .eq(ProcurementSourceBatch::getId, id).set(ProcurementSourceBatch::getLeaseToken, null).set(ProcurementSourceBatch::getLeaseUntil, null));
    }
    @Transactional(rollbackFor = Exception.class)
    public void fail(String id, String token, String code) {
        var batch = lock(id);
        if (batch == null || !"RUNNING".equals(batch.getStatus()) || !Objects.equals(batch.getLeaseToken(), token)
                || batch.getLeaseUntil() == null || !batch.getLeaseUntil().isAfter(LocalDateTime.now())) return;
        batch.setStatus("FAILED"); batch.setErrorCode(code); batch.setRevision(batch.getRevision() + 1);
        var retryAt = batch.getAttempt() < 3 ? LocalDateTime.now().plusSeconds(30L * batch.getAttempt()) : null;
        batches.update(batch, Wrappers.<ProcurementSourceBatch>lambdaUpdate().eq(ProcurementSourceBatch::getTenantId, tenant())
            .eq(ProcurementSourceBatch::getId, id).set(ProcurementSourceBatch::getLeaseToken, null)
            .set(ProcurementSourceBatch::getLeaseUntil, null).set(ProcurementSourceBatch::getNextRetryAt, retryAt));
    }
    @Transactional(rollbackFor = Exception.class)
    public ProcurementSourceBatch cancel(String id, long expectedRevision) {
        var batch = lock(id);
        if (batch == null) throw new ArynBusinessException(403, "来源批次不存在");
        if ("CANCELLED".equals(batch.getStatus())) return batch;
        if (Set.of("CONFIRMED", "SUPERSEDED").contains(batch.getStatus())) throw new ArynBusinessException(409, "已确认来源不可取消");
        if (!Objects.equals(batch.getRevision(), expectedRevision)) throw new ArynBusinessException(409, "批次修订冲突，请重新加载");
        batch.setStatus("CANCELLED"); batch.setRevision(batch.getRevision() + 1); batch.setUpdateTime(LocalDateTime.now());
        batches.update(batch, Wrappers.<ProcurementSourceBatch>lambdaUpdate().eq(ProcurementSourceBatch::getTenantId, tenant())
            .eq(ProcurementSourceBatch::getId, id).set(ProcurementSourceBatch::getLeaseToken, null)
            .set(ProcurementSourceBatch::getLeaseUntil, null).set(ProcurementSourceBatch::getNextRetryAt, null));
        return batch;
    }
    @Transactional(rollbackFor = Exception.class)
    public ProcurementSourceBatch retry(String id, long expectedRevision) {
        var batch = lock(id);
        if (batch == null || !"FAILED".equals(batch.getStatus())) throw new ArynBusinessException(409, "仅失败批次可按原版本重试");
        if (!Objects.equals(batch.getRevision(), expectedRevision)) throw new ArynBusinessException(409, "批次修订冲突，请重新加载");
        batch.setStatus("QUEUED"); batch.setAttempt(0); batch.setRevision(batch.getRevision() + 1);
        batch.setUpdateTime(LocalDateTime.now());
        batches.update(batch, Wrappers.<ProcurementSourceBatch>lambdaUpdate().eq(ProcurementSourceBatch::getTenantId, tenant())
            .eq(ProcurementSourceBatch::getId, id).set(ProcurementSourceBatch::getNextRetryAt, null));
        return batch;
    }
}
