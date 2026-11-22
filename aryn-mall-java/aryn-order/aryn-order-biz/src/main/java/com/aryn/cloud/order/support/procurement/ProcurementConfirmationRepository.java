package com.aryn.cloud.order.support.procurement;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.ProcurementConfirmCommand;
import com.aryn.cloud.order.api.entity.*;
import com.aryn.cloud.order.api.vo.procurement.ProcurementConfirmation;
import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;
import com.aryn.cloud.product.api.support.ReplenishMatchRules;
import com.aryn.cloud.order.mapper.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class ProcurementConfirmationRepository {
    private final ProcurementRepository repository;
    private final ProcurementSourceBatchMapper batches;
    private final ProcurementDraftMapper draftMapper;
    private final ProcurementDraftRowMapper rows;
    private final ProcurementContributionMapper contributions;
    private final ProcurementContributionLedger ledger;
    private final ProcurementCartTarget carts;
    private final ObjectMapper json;
    public List<ProcurementDraftRow> allRows(String batch) {
        var all = rows.selectList(Wrappers.<ProcurementDraftRow>lambdaQuery().eq(ProcurementDraftRow::getTenantId, tenant())
            .eq(ProcurementDraftRow::getBatchId, batch).orderByAsc(ProcurementDraftRow::getSourcePosition).last("LIMIT 2001"));
        if (all.size() > 2000) throw new ArynBusinessException("采购来源超出支持范围"); return all;
    }
    public List<ProcurementContribution> batchContributions(String batch) {
        return contributions.selectList(Wrappers.<ProcurementContribution>lambdaQuery().eq(ProcurementContribution::getTenantId, tenant())
            .eq(ProcurementContribution::getBatchId, batch).last("FOR UPDATE"));
    }
    public List<ProcurementContribution> targetContributions(String type, String targetId) {
        return contributions.selectList(Wrappers.<ProcurementContribution>lambdaQuery().eq(ProcurementContribution::getTenantId, tenant())
            .eq(ProcurementContribution::getTargetType, type).eq(ProcurementContribution::getTargetId, targetId)
            .eq(ProcurementContribution::getStatus, "ACTIVE"));
    }
    public List<ProcurementDraftRow> contributionRows(List<ProcurementContribution> source) {
        if (source.isEmpty()) return List.of();
        return rows.selectList(Wrappers.<ProcurementDraftRow>lambdaQuery()
            .eq(ProcurementDraftRow::getTenantId, tenant())
            .in(ProcurementDraftRow::getId, source.stream().map(ProcurementContribution::getSourceRowId).toList()));
    }
    public ProcurementConfirmation replay(ProcurementSourceBatch batch) {
        try { return json.readValue(batch.getConfirmationJson(), ProcurementConfirmation.class); }
        catch (Exception invalid) { throw new IllegalStateException("已确认来源回执损坏，请联系运营", invalid); }
    }
    private ProcurementSourceBatch lock(String id) {
        return batches.selectOne(Wrappers.<ProcurementSourceBatch>lambdaQuery().eq(ProcurementSourceBatch::getTenantId, tenant())
            .eq(ProcurementSourceBatch::getId, id).last("FOR UPDATE"));
    }
    @Transactional(rollbackFor = Exception.class)
    public ProcurementConfirmation confirm(String actor, String draftId, String batchId, ProcurementConfirmCommand command,
            Map<String, ReplenishImportMatchVO> facts, Map<String, Long> expectedRows, Instant factsAt) {
        String tenant = tenant(); var draft = repository.draft(draftId, true);
        if (draft == null || !actor.equals(draft.getOwnerUserId())) throw new ArynBusinessException(403, "无权访问采购草稿");
        var batch = lock(batchId);
        if (batch == null || !draftId.equals(batch.getDraftId()) || !actor.equals(batch.getOperatorUserId())) throw new ArynBusinessException(403, "无权访问来源");
        if (batch.getConfirmationJson() != null) return replay(batch);
        if (!"OPEN".equals(draft.getStatus()) || !"READY".equals(batch.getStatus()) || !Objects.equals(batch.getRevision(), command.expectedRevision()))
            throw new ArynBusinessException(409, "来源尚有待核对项或修订已变化");
        if (factsAt.isBefore(Instant.now().minusSeconds(30))) throw new ArynBusinessException(409, "商品事实已过期，请重试确认");
        var sourceRows = allRows(batchId);
        if (sourceRows.size() != expectedRows.size() || sourceRows.stream().anyMatch(row -> !Objects.equals(expectedRows.get(row.getId()), row.getRevision())))
            throw new ArynBusinessException(409, "核对结果已变化，请重新加载");
        if (sourceRows.stream().anyMatch(row -> !Set.of("CONFIRMED", "REJECTED").contains(Objects.toString(row.getReviewStatus(), ""))))
            throw new ArynBusinessException(409, "请先完成来源逐行核对");
        String type = command.targetType(); String targetId = "DIRECT".equals(type) ? draftId : command.targetId();
        if ("SHARED_CART".equals(type)) carts.lock(tenant, actor, targetId, draft);
        var oldContributions = new ArrayList<ProcurementContribution>();
        ProcurementSourceBatch old = null;
        if ("REPLACE".equals(command.mode())) {
            if (batchId.equals(command.replaceBatchId())) throw new ArynBusinessException("不能用来源替换自身");
            old = lock(command.replaceBatchId());
            if (old == null || !draftId.equals(old.getDraftId()) || !actor.equals(old.getOperatorUserId())
                    || !"CONFIRMED".equals(old.getStatus()) || !Objects.equals(old.getRevision(), command.replaceBatchRevision()))
                throw new ArynBusinessException(409, "被替换来源已变化或无权替换");
            var prior = replay(old);
            if (!type.equals(prior.targetType()) || !targetId.equals(prior.targetId())) throw new ArynBusinessException("来源仅能在原目标内替换");
            oldContributions.addAll(batchContributions(old.getId()));
            if (oldContributions.stream().anyMatch(value -> !"ACTIVE".equals(value.getStatus()) || !"BATCH".equals(value.getOrigin())))
                throw new ArynBusinessException(409, "原来源已被手工改量或无法重建，禁止自动相减");
        }
        Map<String, SharedCartItem> shared = new LinkedHashMap<>();
        Map<String, Integer> totals = new LinkedHashMap<>(); Map<String, String> lineSku = new LinkedHashMap<>();
        Map<String, String> newLineByRow = new LinkedHashMap<>(); Map<String, String> ownerByRow = new LinkedHashMap<>();
        for (var contribution : oldContributions) {
            if ("SHARED_CART".equals(type)) {
                var target = carts.existing(tenant, targetId, contribution.getTargetLineId());
                shared.put(target.getId(), target); totals.put(target.getId(), target.getRequestedQuantity());
            } else totals.put(contribution.getTargetLineId(), ledger.sum(ledger.active(type, contribution.getTargetLineId())));
            lineSku.put(contribution.getTargetLineId(), contribution.getSkuId());
        }
        for (var row : sourceRows) if ("CONFIRMED".equals(row.getReviewStatus())) {
            var fact = facts.get(row.getSelectedSkuId()); requireRow(row, fact);
            String line;
            if ("SHARED_CART".equals(type)) {
                var owner = carts.owner(tenant, actor, targetId, row.getRawOwner());
                var target = carts.line(tenant, targetId, owner, fact.getSkuId(), fact.getSpuId());
                line = target.getId(); shared.put(line, target); totals.putIfAbsent(line, target.getRequestedQuantity());
                ownerByRow.put(row.getId(), owner.userId());
                ledger.baseline(type, targetId, line, fact.getSkuId(), owner.userId(), target.getRequestedQuantity());
            } else {
                line = UUID.nameUUIDFromBytes((draftId + ":" + fact.getSkuId()).getBytes(StandardCharsets.UTF_8)).toString();
                totals.putIfAbsent(line, ledger.sum(ledger.active(type, line))); ownerByRow.put(row.getId(), actor);
            }
            lineSku.put(line, fact.getSkuId()); newLineByRow.put(row.getId(), line);
        }
        for (var entry : totals.entrySet()) ledger.requireConsistent(type, entry.getKey(), entry.getValue());
        for (var contribution : oldContributions) {
            totals.compute(contribution.getTargetLineId(), (key, total) -> Math.subtractExact(total, contribution.getQuantity()));
            ledger.revoke(contribution);
        }
        for (var row : sourceRows) if ("CONFIRMED".equals(row.getReviewStatus())) {
            String line = newLineByRow.get(row.getId());
            totals.compute(line, (key, total) -> Math.addExact(total, row.getConfirmedSalesQuantity()));
            ledger.insert(draftId, batchId, row.getId(), type, targetId, line, ownerByRow.get(row.getId()),
                row.getSelectedSkuId(), row.getConfirmedSalesQuantity(), "BATCH");
        }
        var skuTotals = new LinkedHashMap<String, Integer>();
        for (var entry : totals.entrySet()) {
            if (entry.getValue() < 0) throw new ArynBusinessException(409, "来源贡献与当前目标冲突");
            if (entry.getValue() > 0) skuTotals.merge(lineSku.get(entry.getKey()), entry.getValue(), Math::addExact);
        }
        for (var entry : shared.entrySet()) carts.quantity(entry.getValue(), totals.get(entry.getKey()));
        if ("SHARED_CART".equals(type)) {
            skuTotals.clear(); skuTotals.putAll(carts.quantities(tenant, targetId));
        }
        for (var entry : skuTotals.entrySet()) requireQuantity(facts.get(entry.getKey()), entry.getValue());
        var active = contributions.selectList(Wrappers.<ProcurementContribution>lambdaQuery().eq(ProcurementContribution::getTenantId, tenant)
            .eq(ProcurementContribution::getTargetType, type).eq(ProcurementContribution::getTargetId, targetId)
            .eq(ProcurementContribution::getStatus, "ACTIVE").last("FOR UPDATE"));
        var resultItems = new ArrayList<ProcurementConfirmation.Item>();
        var bySku = new LinkedHashMap<String, List<ProcurementContribution>>();
        active.forEach(value -> bySku.computeIfAbsent(value.getSkuId(), key -> new ArrayList<>()).add(value));
        for (var entry : bySku.entrySet()) {
            int quantity = ledger.sum(entry.getValue()); if (quantity == 0) continue;
            var fact = facts.get(entry.getKey());
            if ("DIRECT".equals(type)) requireQuantity(fact, quantity);
            resultItems.add(new ProcurementConfirmation.Item(entry.getKey(), fact == null ? null : fact.getSpuId(), quantity,
                entry.getValue().stream().filter(value -> "BATCH".equals(value.getOrigin())).map(ProcurementContribution::getSourceRowId).toList()));
        }
        batch.setStatus("CONFIRMED"); batch.setRevision(batch.getRevision() + 1); batch.setConfirmedRequestId(command.requestId());
        var result = new ProcurementConfirmation(batchId, type, targetId, "CONFIRMED", batch.getRevision(), resultItems);
        try { batch.setConfirmationJson(json.writeValueAsString(result)); }
        catch (Exception invalid) { throw new IllegalStateException(invalid); }
        batch.setUpdateBy(actor); batch.setUpdateTime(LocalDateTime.now()); batches.updateById(batch);
        if (old != null) { old.setStatus("SUPERSEDED"); old.setReplacedByBatchId(batchId); old.setRevision(old.getRevision() + 1); batches.updateById(old); }
        draft.setRevision(draft.getRevision() + 1); draft.setUpdateTime(LocalDateTime.now());
        draftMapper.updateById(draft);
        // 草稿保持OPEN，可以再次加入来源；每个来源的确认状态不可逆重算。
        // 使用既有仓储中的锁，不引入跨域事务。
        return result;
    }
    private void requireRow(ProcurementDraftRow row, ReplenishImportMatchVO fact) {
        if (fact == null || fact.getMeasurement() == null || !Objects.equals(row.getMeasurementRevision(), fact.getMeasurement().measurementRevision()))
            throw new ArynBusinessException(409, "商品度量事实已变化，请重新核对该行");
        requireQuantity(fact, row.getConfirmedSalesQuantity());
    }
    private void requireQuantity(ReplenishImportMatchVO fact, Integer quantity) {
        if (fact == null || !"0".equals(fact.getSkuStatus()) || !"1".equals(fact.getSpuStatus()) || fact.getMeasurement() == null
                || !ReplenishMatchRules.quantityAcceptable(quantity, fact.getMeasurement().minOrderQty(), fact.getMeasurement().stepQty())
                || fact.getStock() == null || quantity > fact.getStock()) throw new ArynBusinessException(409, "商品上下架、库存或数量门槛已变化，请重新核对");
    }
    private String tenant() {
        String tenant = ArynTenantContextHolder.getTenantId(); if (tenant == null || tenant.isBlank()) throw new ArynBusinessException(403, "缺少租户上下文"); return tenant;
    }
}
