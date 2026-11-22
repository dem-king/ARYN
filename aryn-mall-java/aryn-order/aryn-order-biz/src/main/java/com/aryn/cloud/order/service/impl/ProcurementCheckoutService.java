package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.vo.procurement.ProcurementConfirmation;
import com.aryn.cloud.order.support.procurement.ProcurementConfirmationRepository;
import com.aryn.cloud.product.api.remote.RemoteReplenishImportMatchService;
import com.aryn.cloud.product.api.support.ReplenishMatchRules;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import java.util.*;

/** 恢复个人结算使用当前有效贡献，旧批次的不可变回执只作审计。 */
@Service
@RequiredArgsConstructor
public class ProcurementCheckoutService {
    private final ProcurementDraftService drafts;
    private final ProcurementConfirmationRepository repository;
    @DubboReference(check = false) private RemoteReplenishImportMatchService products;
    public ProcurementConfirmation current(String tenant, String actor, String draftId) {
        var draft = drafts.get(tenant, actor, draftId);
        var contributions = repository.targetContributions("DIRECT", draftId);
        var sourceRows = new HashMap<String, com.aryn.cloud.order.api.entity.ProcurementDraftRow>();
        for (int cursor = 0; cursor < contributions.size(); cursor += 100) {
            repository.contributionRows(contributions.subList(cursor, Math.min(contributions.size(), cursor + 100)))
                .forEach(row -> sourceRows.put(row.getId(), row));
        }
        var bySku = new LinkedHashMap<String, List<com.aryn.cloud.order.api.entity.ProcurementContribution>>();
        contributions.forEach(value -> bySku.computeIfAbsent(value.getSkuId(), key -> new ArrayList<>()).add(value));
        var result = new ArrayList<ProcurementConfirmation.Item>();
        var ids = new ArrayList<>(bySku.keySet());
        for (int cursor = 0; cursor < ids.size(); cursor += 100) {
            var facts = products.matchSkuIds(tenant, ids.subList(cursor, Math.min(ids.size(), cursor + 100)));
            if (facts == null) throw new ArynBusinessException("商品事实暂不可用，请稍后重试");
            for (String id : ids.subList(cursor, Math.min(ids.size(), cursor + 100))) {
                var fact = facts.stream().filter(value -> id.equals(value.getSkuId())).findFirst().orElse(null);
                var source = bySku.get(id); long total = source.stream().mapToLong(value -> value.getQuantity()).sum();
                if (total <= 0 || total > Integer.MAX_VALUE || fact == null || fact.getMeasurement() == null
                        || !"0".equals(fact.getSkuStatus()) || !"1".equals(fact.getSpuStatus())
                        || !ReplenishMatchRules.quantityAcceptable((int) total, fact.getMeasurement().minOrderQty(), fact.getMeasurement().stepQty())
                        || fact.getStock() == null || total > fact.getStock()) throw new ArynBusinessException(409, "当前商品或数量门槛已变化，请重新核对采购");
                for (var contribution : source) {
                    var row = sourceRows.get(contribution.getSourceRowId());
                    if (row == null || !"CONFIRMED".equals(row.getReviewStatus())
                            || !id.equals(row.getSelectedSkuId())
                            || !Objects.equals(contribution.getQuantity(), row.getConfirmedSalesQuantity())
                            || !Objects.equals(row.getMeasurementRevision(), fact.getMeasurement().measurementRevision()))
                        throw new ArynBusinessException(409, "已确认商品度量事实已变化，请重新核对采购来源");
                }
                result.add(new ProcurementConfirmation.Item(id, fact.getSpuId(), (int) total, source.stream().map(value -> value.getSourceRowId()).toList()));
            }
        }
        if (!Objects.equals(draft.getRevision(), drafts.get(tenant, actor, draftId).getRevision()))
            throw new ArynBusinessException(409, "采购来源已变化，请重新加载当前清单");
        return new ProcurementConfirmation(null, "DIRECT", draftId, "CURRENT", draft.getRevision(), result);
    }
}
