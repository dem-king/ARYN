package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.entity.*;
import com.aryn.cloud.product.api.dto.ReplenishImportMatchDTO;
import com.aryn.cloud.product.api.remote.RemoteReplenishImportMatchService;
import com.aryn.cloud.product.api.support.*;
import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ProcurementMatchingService {
    @DubboReference(check = false) private RemoteReplenishImportMatchService products;
    private final ObjectMapper json;

    public List<ProcurementDraftRow> match(ProcurementSourceBatch batch, ProcurementExtractionService.Extraction extraction) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("商品RPC不能占用数据库事务");
        String tenant = ArynTenantContextHolder.getTenantId();
        if (tenant == null || !tenant.equals(batch.getTenantId())) throw new IllegalArgumentException("采购租户上下文不一致");
        var inputs = new ArrayList<ReplenishImportMatchDTO>();
        for (int i = 0; i < extraction.rows().size(); i++) {
            var source = extraction.rows().get(i); var input = new ReplenishImportMatchDTO();
            input.setRowNo(i + 1); input.setName(bounded(source.name(), 500)); input.setSpec(bounded(source.spec(), 500));
            input.setCode(bounded(source.code(), 200)); input.setCodeType(source.codeType()); input.setFuzzy("TEXT".equals(batch.getInputType()));
            // 匹配不以整数数量猜测原始需求，单位规则在命中后执行。
            inputs.add(input);
        }
        List<ReplenishImportMatchVO> matches;
        if (Set.of("HISTORICAL", "REORDER", "FREQUENT").contains(batch.getInputType())) {
            var ids = extraction.rows().stream().map(ProcurementExtractionService.Source::code).filter(Objects::nonNull).distinct().toList();
            var facts = products.matchSkuIds(tenant, ids);
            if (facts == null) throw new IllegalStateException("历史商品事实不可用");
            matches = new ArrayList<>();
            for (int index = 0; index < extraction.rows().size(); index++) {
                var source = extraction.rows().get(index);
                var match = new ReplenishImportMatchVO();
                var fact = facts.stream().filter(value -> Objects.equals(source.code(), value.getSkuId())).findFirst().orElse(null);
                if (fact != null) org.springframework.beans.BeanUtils.copyProperties(fact, match);
                match.setRowNo(index + 1); match.setMatchType(fact == null ? "NONE" : "CODE"); matches.add(match);
            }
        } else matches = products.matchRows(tenant, inputs);
        if (matches == null || matches.size() != inputs.size()) throw new IllegalStateException("商品匹配未完整覆盖来源行");
        var output = new ArrayList<ProcurementDraftRow>();
        for (int i = 0; i < extraction.rows().size(); i++) {
            var source = extraction.rows().get(i); var match = matches.get(i);
            if (match == null || !Objects.equals(match.getRowNo(), i + 1)) throw new IllegalStateException("商品匹配来源顺序不一致");
            var row = new ProcurementDraftRow(); row.setId(UUID.randomUUID().toString());
            row.setTenantId(tenant); row.setDraftId(batch.getDraftId()); row.setBatchId(batch.getId()); row.setParseRevision(batch.getParseRevision());
            row.setSourcePosition(source.position()); row.setSourceSheet(source.sheet()); row.setSourceRow(source.row()); row.setSourceOffset(source.offset());
            row.setRawText(source.rawText()); row.setRawName(bounded(source.name(), 500)); row.setRawQuantity(source.quantity());
            row.setRawUnit(bounded(source.unit(), 24)); row.setRawSpec(bounded(source.spec(), 500)); row.setRawCode(bounded(source.code(), 200));
            if (Set.of("HISTORICAL", "REORDER", "FREQUENT").contains(batch.getInputType()) && row.getRawName() == null)
                row.setRawName(bounded(match.getName(), 500));
            row.setRawOwner(bounded(source.owner(), 120)); row.setRevision(0L); row.setDelFlag("0");
            row.setCreateBy(batch.getOperatorUserId()); row.setUpdateBy(batch.getOperatorUserId());
            row.setCreateTime(LocalDateTime.now()); row.setUpdateTime(row.getCreateTime());
            row.setParseStatus(source.warning() == null && extraction.warning() == null ? "PARSED" : "REVIEW_REQUIRED");
            row.setMatchStatus(match.getSkuId() == null ? match.getMatchType() : "MATCHED");
            row.setMatchedSkuId(match.getSkuId()); row.setSelectedSkuId(match.getSkuId());
            row.setUnitStatus("UNKNOWN"); row.setQuantityStatus(source.quantity() == null ? "MISSING" : "UNKNOWN"); row.setReviewStatus("REQUIRED");
            ProductMeasurementRules.Decision decision = null;
            if (match.getSkuId() != null && match.getMeasurement() != null) {
                decision = ProductMeasurementRules.convert(source.quantity(), source.unit(),
                    source.rawText().matches(".*(约|大概|左右|大约).*"), match.getMeasurement());
                applyDecision(row, decision);
                if (!"0".equals(match.getSkuStatus()) || !"1".equals(match.getSpuStatus())) row.setMatchStatus("OFF_SHELF");
                else if (!ReplenishMatchRules.specEquivalent(source.spec(), match.getSpec())) row.setMatchStatus("SPEC_MISMATCH");
                else if (match.getStock() == null) row.setQuantityStatus("STOCK_UNKNOWN");
                else if (decision.suggestedSalesQuantity() != null && match.getStock() < decision.suggestedSalesQuantity()) row.setQuantityStatus("OVER_STOCK");
                else if (!decision.requiresReview() && "PARSED".equals(row.getParseStatus())
                        && ("CODE".equals(match.getMatchType()) || ReplenishMatchRules.normalizeName(source.name()).equals(ReplenishMatchRules.normalizeName(match.getName())))) {
                    row.setReviewStatus("CONFIRMED"); row.setConfirmedSalesQuantity(decision.suggestedSalesQuantity());
                }
            }
            if (row.getRawName() == null || (source.spec() != null && row.getRawSpec() == null)
                    || (source.unit() != null && row.getRawUnit() == null)) {
                row.setParseStatus("REVIEW_REQUIRED"); row.setReviewStatus("REQUIRED"); row.setConfirmedSalesQuantity(null);
            }
            try {
                row.setCandidatesJson(json.writeValueAsString(match.getCandidates() == null ? List.of() : match.getCandidates().stream().limit(20).toList()));
                Map<String, Object> evidence = new LinkedHashMap<>(); evidence.put("parserSource", extraction.parserSource());
                evidence.put("parseWarning", source.warning()); evidence.put("fallbackWarning", extraction.warning());
                evidence.put("configVersion", batch.getConfigVersion()); evidence.put("templateVersion", batch.getTemplateVersion());
                evidence.put("codeType", source.codeType()); evidence.put("measurement", match.getMeasurement()); evidence.put("decision", decision);
                evidence.put("matchedName", match.getName()); evidence.put("matchedSpec", match.getSpec()); evidence.put("stock", match.getStock());
                row.setDecisionJson(json.writeValueAsString(evidence));
            } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalStateException("采购证据序列化失败", invalid); }
            output.add(row);
        }
        return output;
    }
    public static void applyDecision(ProcurementDraftRow row, ProductMeasurementRules.Decision decision) {
        row.setNormalizedBaseQuantity(decision.normalizedBaseQuantity()); row.setSuggestedSalesQuantity(decision.suggestedSalesQuantity());
        row.setExcessBaseQuantity(decision.excessBaseQuantity()); row.setUnitStatus(decision.unitStatus());
        row.setQuantityStatus(decision.quantityStatus()); row.setMeasurementRevision(decision.measurementRevision());
    }
    private static String bounded(String value, int limit) { return value == null || value.length() > limit ? null : value; }
}
