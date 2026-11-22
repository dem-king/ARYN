package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.ProcurementReviewCommand;
import com.aryn.cloud.order.api.dto.ProcurementBulkReviewCommand;
import com.aryn.cloud.order.api.entity.ProcurementDraftRow;
import com.aryn.cloud.order.support.procurement.*;
import com.aryn.cloud.product.api.remote.RemoteReplenishImportMatchService;
import com.aryn.cloud.product.api.support.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProcurementReviewService {
    private final ProcurementDraftService drafts;
    private final ProcurementReviewRepository repository;
    private final ObjectMapper json;
    @DubboReference(check = false) private RemoteReplenishImportMatchService products;
    public record BulkOutcome(List<ProcurementReviewRepository.Outcome> applied, List<Problem> problems) { }
    public record Problem(String rowId, String code, String message, Long revision) { }

    /** 每行是独立短事务；失败行保留待核对，不把部分成功冒充全部成功。 */
    public BulkOutcome reviewReady(String tenant, String actor, String draftId, String batchId,
            ProcurementBulkReviewCommand command) {
        if (command == null || command.rows() == null || command.rows().isEmpty() || command.rows().size() > 100
                || command.reason() == null || command.reason().isBlank() || command.reason().length() > 500)
            throw new ArynBusinessException("请选择1至100行并填写最多500字的核对原因");
        Set<String> ids = new HashSet<>();
        for (var item : command.rows()) {
            if (item == null || item.rowId() == null || item.rowId().isBlank() || !ids.add(item.rowId()))
                throw new ArynBusinessException("采购行不可为空或重复");
        }
        drafts.batch(tenant, actor, draftId, batchId);
        List<ProcurementReviewRepository.Outcome> applied = new ArrayList<>();
        List<Problem> problems = new ArrayList<>();
        for (var item : command.rows()) {
            var current = repository.row(batchId, item.rowId());
            if (current == null || !Objects.equals(current.getRevision(), item.expectedRevision())) {
                problems.add(new Problem(item.rowId(), "CONFLICT", "采购行已变化，请重载", current == null ? null : current.getRevision()));
                continue;
            }
            if (!"PARSED".equals(current.getParseStatus()) || !"REQUIRED".equals(current.getReviewStatus()) || !"MATCHED".equals(current.getMatchStatus())
                    || !"EXACT".equals(current.getUnitStatus()) || !"VALID".equals(current.getQuantityStatus())) {
                problems.add(new Problem(item.rowId(), "REVIEW_REQUIRED", "该行需单独核对数量、单位或商品", current.getRevision()));
                continue;
            }
            try {
                var outcome = review(tenant, actor, draftId, batchId, item.rowId(), new ProcurementReviewCommand(
                    item.expectedRevision(), "ACCEPT_SUGGESTION", current.getSelectedSkuId(), null, false, command.reason()));
                if ("APPLIED".equals(outcome.decision())) applied.add(outcome);
                else problems.add(new Problem(item.rowId(), "CONFLICT", "采购行已变化，请重载", outcome.row().getRevision()));
            } catch (ArynBusinessException invalid) {
                problems.add(new Problem(item.rowId(), "REVIEW_REQUIRED", invalid.getMessage(), current.getRevision()));
            }
        }
        return new BulkOutcome(List.copyOf(applied), List.copyOf(problems));
    }
    public ProcurementReviewRepository.Outcome review(String tenant, String actor, String draftId, String batchId,
            String rowId, ProcurementReviewCommand command) {
        if (command == null || command.action() == null || !Set.of("REJECT", "SELECT_SKU", "CONFIRM_SALES", "ACCEPT_SUGGESTION", "SET_OWNER").contains(command.action())
                || command.reason() == null || command.reason().isBlank() || command.reason().length() > 500)
            throw new ArynBusinessException("请选择核对动作并填写最多500字的原因");
        drafts.batch(tenant, actor, draftId, batchId);
        var row = repository.row(batchId, rowId);
        if (row == null) throw new ArynBusinessException(403, "采购行不存在");
        var proposal = new ProcurementDraftRow();
        org.springframework.beans.BeanUtils.copyProperties(row, proposal);
        if ("SET_OWNER".equals(command.action())) {
            if (command.ownerName() == null || command.ownerName().length() > 120)
                throw new ArynBusinessException("归属姓名最多120字");
            proposal.setRawOwner(command.ownerName().trim()); proposal.setReviewReason(command.reason());
            return repository.apply(actor, command.action(), command.expectedRevision(), proposal, Instant.now());
        }
        proposal.setId(rowId); proposal.setBatchId(batchId);
        proposal.setReviewReason(command.reason()); proposal.setSelectedSkuId(row.getSelectedSkuId());
        proposal.setConfirmedSalesQuantity(null);
        proposal.setReviewStatus("REJECTED"); proposal.setMatchStatus(row.getMatchStatus());
        proposal.setUnitStatus(row.getUnitStatus()); proposal.setQuantityStatus(row.getQuantityStatus());
        Instant factsAt = Instant.now();
        if (!"REJECT".equals(command.action())) {
            if (command.selectedSkuId() == null || command.selectedSkuId().isBlank()) throw new ArynBusinessException("请先选择商品");
            // RPC在事务外，保存再校验30秒事实有效期；下单仍重新读取最新事实。
            var facts = products.matchSkuIds(tenant, List.of(command.selectedSkuId()));
            var fact = facts == null ? null : facts.stream().filter(value -> command.selectedSkuId().equals(value.getSkuId())).findFirst().orElse(null);
            if (fact == null || !"0".equals(fact.getSkuStatus()) || !"1".equals(fact.getSpuStatus()) || fact.getMeasurement() == null)
                throw new ArynBusinessException("该商品已不可售，请重新选择");
            var decision = ProductMeasurementRules.convert(row.getRawQuantity(), row.getRawUnit(),
                row.getRawText() != null && row.getRawText().matches(".*(约|大概|左右|大约).*"), fact.getMeasurement());
            ProcurementMatchingService.applyDecision(proposal, decision);
            proposal.setSelectedSkuId(fact.getSkuId()); proposal.setMatchStatus("MATCHED"); proposal.setReviewStatus("REQUIRED");
            if (!"SELECT_SKU".equals(command.action())) {
                Integer quantity;
                if ("CONFIRM_SALES".equals(command.action())) {
                    quantity = command.salesQuantity();
                    proposal.setUnitStatus("MANUAL_SALES"); proposal.setQuantityStatus("VALID");
                } else {
                    quantity = decision.suggestedSalesQuantity();
                    if (quantity == null || !Set.of("EXACT", "APPROX").contains(decision.unitStatus())
                            || decision.requiresReview() && !command.acceptRounding()) throw new ArynBusinessException("请明确确认取整或约量提示，未知单位须改填销售件数");
                    if (!ReplenishMatchRules.specEquivalent(row.getRawSpec(), fact.getSpec())) throw new ArynBusinessException("规格已变更，请按当前商品明确填写销售件数");
                }
                if (!ReplenishMatchRules.quantityAcceptable(quantity, fact.getMeasurement().minOrderQty(), fact.getMeasurement().stepQty()))
                    throw new ArynBusinessException("销售数量不满足当前起订量和步长");
                if (fact.getStock() == null || quantity > fact.getStock()) throw new ArynBusinessException("当前库存不足或未知，请重新核对");
                proposal.setConfirmedSalesQuantity(quantity); proposal.setReviewStatus("CONFIRMED");
            }
            try { proposal.setDecisionJson(json.writeValueAsString(Map.of("decision", decision, "measurement", fact.getMeasurement(), "skuId", fact.getSkuId(), "stock", Objects.requireNonNullElse(fact.getStock(), -1)))); }
            catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalStateException(invalid); }
        }
        return repository.apply(actor, command.action(), command.expectedRevision(), proposal, factsAt);
    }
}
