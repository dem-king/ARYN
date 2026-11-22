package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.ProcurementConfirmCommand;
import com.aryn.cloud.order.api.vo.procurement.ProcurementConfirmation;
import com.aryn.cloud.order.support.procurement.*;
import com.aryn.cloud.order.service.ISharedCartService;
import com.aryn.cloud.product.api.remote.RemoteReplenishImportMatchService;
import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProcurementConfirmationService {
    private final ProcurementDraftService drafts;
    private final ProcurementConfirmationRepository repository;
    private final ISharedCartService shared;
    private final ProcurementCartTarget targets;
    @DubboReference(check = false) private RemoteReplenishImportMatchService products;
    public ProcurementConfirmation confirm(String tenant, String actor, String draftId, String batchId, ProcurementConfirmCommand command) {
        if (command == null || !Set.of("ADD", "REPLACE").contains(Objects.toString(command.mode(), ""))
                || !Set.of("DIRECT", "SHARED_CART").contains(Objects.toString(command.targetType(), ""))) throw new ArynBusinessException("请选择追加或替换及采购目标");
        ProcurementDraftService.requestId(command.requestId());
        var batch = drafts.batch(tenant, actor, draftId, batchId);
        if (batch.getConfirmationJson() != null) return repository.replay(batch);
        if (!"READY".equals(batch.getStatus())) throw new ArynBusinessException(409, "请先完成全部来源核对或拒绝未采购行");
        var rows = repository.allRows(batchId); Set<String> skuIds = new LinkedHashSet<>();
        rows.stream().filter(row -> "CONFIRMED".equals(row.getReviewStatus())).map(row -> row.getSelectedSkuId()).filter(Objects::nonNull).forEach(skuIds::add);
        String targetId = "DIRECT".equals(command.targetType()) ? draftId : command.targetId();
        if ("SHARED_CART".equals(command.targetType())) {
            shared.getCartForUser(tenant, actor, targetId); // 禁止先按任意cartId获取目标SKU集合。
            skuIds.addAll(targets.quantities(tenant, targetId).keySet());
        }
        repository.targetContributions(command.targetType(), targetId).stream().map(value -> value.getSkuId()).forEach(skuIds::add);
        if ("REPLACE".equals(command.mode())) {
            if (command.replaceBatchId() == null || command.replaceBatchRevision() == null) throw new ArynBusinessException("请指定被替换来源和修订号");
            drafts.batch(tenant, actor, draftId, command.replaceBatchId());
            repository.batchContributions(command.replaceBatchId()).stream().map(value -> value.getSkuId()).forEach(skuIds::add);
        }
        var ids = new ArrayList<>(skuIds); Map<String, ReplenishImportMatchVO> facts = new HashMap<>();
        Instant loadedAt = Instant.now();
        for (int cursor = 0; cursor < ids.size(); cursor += 100) {
            var response = products.matchSkuIds(tenant, ids.subList(cursor, Math.min(ids.size(), cursor + 100)));
            if (response == null) throw new ArynBusinessException("商品事实暂不可用，请稍后确认");
            response.forEach(value -> facts.put(value.getSkuId(), value));
        }
        return repository.confirm(actor, draftId, batchId, command, facts,
            rows.stream().collect(Collectors.toMap(row -> row.getId(), row -> row.getRevision())), loadedAt);
    }
}
