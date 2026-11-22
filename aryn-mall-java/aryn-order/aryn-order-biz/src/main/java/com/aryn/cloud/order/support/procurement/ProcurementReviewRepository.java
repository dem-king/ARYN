package com.aryn.cloud.order.support.procurement;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.*;
import com.aryn.cloud.order.mapper.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class ProcurementReviewRepository {
    private final ProcurementSourceBatchMapper batches;
    private final ProcurementDraftRowMapper rows;
    private final ObjectMapper json;
    public record Outcome(String decision, ProcurementDraftRow row, long batchRevision) { }
    public ProcurementDraftRow row(String batchId, String rowId) {
        return rows.selectOne(Wrappers.<ProcurementDraftRow>lambdaQuery()
            .eq(ProcurementDraftRow::getTenantId, tenant()).eq(ProcurementDraftRow::getBatchId, batchId)
            .eq(ProcurementDraftRow::getId, rowId));
    }
    private String tenant() {
        String value = ArynTenantContextHolder.getTenantId();
        if (value == null || value.isBlank()) throw new ArynBusinessException(403, "缺少租户上下文"); return value;
    }
    @Transactional(rollbackFor = Exception.class)
    public Outcome apply(String actor, String action, long expectedRevision, ProcurementDraftRow proposal, Instant factsAt) {
        if (factsAt.isBefore(Instant.now().minusSeconds(30))) throw new ArynBusinessException(409, "商品事实已过期，请重试核对");
        var batch = batches.selectOne(Wrappers.<ProcurementSourceBatch>lambdaQuery()
            .eq(ProcurementSourceBatch::getTenantId, tenant()).eq(ProcurementSourceBatch::getId, proposal.getBatchId()).last("FOR UPDATE"));
        if (batch == null || !actor.equals(batch.getOperatorUserId()) || !Set.of("REVIEW_REQUIRED", "READY").contains(batch.getStatus()))
            throw new ArynBusinessException(409, "批次当前不可核对");
        var existing = rows.selectOne(Wrappers.<ProcurementDraftRow>lambdaQuery()
            .eq(ProcurementDraftRow::getTenantId, tenant()).eq(ProcurementDraftRow::getBatchId, batch.getId())
            .eq(ProcurementDraftRow::getId, proposal.getId()).last("FOR UPDATE"));
        if (existing == null) throw new ArynBusinessException(403, "采购行不存在");
        if (!Objects.equals(existing.getRevision(), expectedRevision)) return new Outcome("CONFLICT", existing, batch.getRevision());
        String evidence;
        try {
            var root = existing.getDecisionJson() == null ? json.createObjectNode() : (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(existing.getDecisionJson());
            var history = root.withArray("reviews");
            if (history.size() >= 1000) throw new ArynBusinessException("该来源核对记录过多，请另开采购来源");
            var entry = json.createObjectNode(); entry.put("actor", actor); entry.put("action", action);
            entry.put("at", Instant.now().toString()); entry.put("reason", proposal.getReviewReason());
            entry.put("fromRevision", expectedRevision); entry.put("toRevision", expectedRevision + 1);
            entry.set("before", json.valueToTree(values(existing))); entry.set("after", json.valueToTree(values(proposal))); history.add(entry);
            root.set("latestReviewFacts", proposal.getDecisionJson() == null ? json.nullNode() : json.readTree(proposal.getDecisionJson()));
            evidence = json.writeValueAsString(root);
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalStateException("核对证据损坏", invalid); }
        int changed = rows.update(null, Wrappers.<ProcurementDraftRow>lambdaUpdate()
            .eq(ProcurementDraftRow::getTenantId, tenant()).eq(ProcurementDraftRow::getId, existing.getId())
            .eq(ProcurementDraftRow::getRevision, expectedRevision)
            .set(ProcurementDraftRow::getRawOwner, proposal.getRawOwner())
            .set(ProcurementDraftRow::getSelectedSkuId, proposal.getSelectedSkuId())
            .set(ProcurementDraftRow::getMatchStatus, proposal.getMatchStatus()).set(ProcurementDraftRow::getUnitStatus, proposal.getUnitStatus())
            .set(ProcurementDraftRow::getQuantityStatus, proposal.getQuantityStatus()).set(ProcurementDraftRow::getReviewStatus, proposal.getReviewStatus())
            .set(ProcurementDraftRow::getNormalizedBaseQuantity, proposal.getNormalizedBaseQuantity())
            .set(ProcurementDraftRow::getSuggestedSalesQuantity, proposal.getSuggestedSalesQuantity())
            .set(ProcurementDraftRow::getConfirmedSalesQuantity, proposal.getConfirmedSalesQuantity())
            .set(ProcurementDraftRow::getExcessBaseQuantity, proposal.getExcessBaseQuantity())
            .set(ProcurementDraftRow::getMeasurementRevision, proposal.getMeasurementRevision())
            .set(ProcurementDraftRow::getDecisionJson, evidence).set(ProcurementDraftRow::getReviewActor, actor)
            .set(ProcurementDraftRow::getReviewReason, proposal.getReviewReason()).set(ProcurementDraftRow::getRevision, expectedRevision + 1)
            .set(ProcurementDraftRow::getUpdateBy, actor).set(ProcurementDraftRow::getUpdateTime, LocalDateTime.now()));
        if (changed != 1) throw new ArynBusinessException(409, "采购行修订冲突");
        long pending = rows.selectCount(Wrappers.<ProcurementDraftRow>lambdaQuery()
            .eq(ProcurementDraftRow::getTenantId, tenant()).eq(ProcurementDraftRow::getBatchId, batch.getId())
            .notIn(ProcurementDraftRow::getReviewStatus, List.of("CONFIRMED", "REJECTED")));
        batch.setStatus(pending == 0 ? "READY" : "REVIEW_REQUIRED"); batch.setRevision(batch.getRevision() + 1);
        batch.setUpdateBy(actor); batch.setUpdateTime(LocalDateTime.now()); batches.updateById(batch);
        return new Outcome("APPLIED", row(batch.getId(), existing.getId()), batch.getRevision());
    }
    private Map<String, Object> values(ProcurementDraftRow row) {
        Map<String, Object> values = new LinkedHashMap<>(); values.put("ownerName", row.getRawOwner()); values.put("skuId", row.getSelectedSkuId());
        values.put("salesQuantity", row.getConfirmedSalesQuantity()); values.put("suggestedQuantity", row.getSuggestedSalesQuantity());
        values.put("unitStatus", row.getUnitStatus()); values.put("quantityStatus", row.getQuantityStatus());
        values.put("reviewStatus", row.getReviewStatus()); values.put("measurementRevision", row.getMeasurementRevision()); return values;
    }
}
