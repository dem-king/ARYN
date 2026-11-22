package com.aryn.cloud.order.support.procurement;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.ProcurementContribution;
import com.aryn.cloud.order.mapper.ProcurementContributionMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.*;

/** 调用方已锁目标行/父购物车；只撤销有来源的贡献，无法解释的变更显式冲突。 */
@Component
@RequiredArgsConstructor
public class ProcurementContributionLedger {
    private final ProcurementContributionMapper mapper;
    public List<ProcurementContribution> active(String type, String lineId) {
        return mapper.selectList(Wrappers.<ProcurementContribution>lambdaQuery()
            .eq(ProcurementContribution::getTenantId, tenant()).eq(ProcurementContribution::getTargetType, type)
            .eq(ProcurementContribution::getTargetLineId, lineId).eq(ProcurementContribution::getStatus, "ACTIVE").last("FOR UPDATE"));
    }
    public int sum(List<ProcurementContribution> rows) {
        long total = rows.stream().mapToLong(ProcurementContribution::getQuantity).sum();
        if (total < 0 || total > Integer.MAX_VALUE) throw new ArynBusinessException(409, "来源数量账本异常，请人工核对");
        return (int) total;
    }
    public void requireConsistent(String type, String lineId, int current) {
        if (sum(active(type, lineId)) != current) throw new ArynBusinessException(409, "目标数量已被修改，来源不可安全替换，请人工核对");
    }
    public void baseline(String type, String targetId, String lineId, String sku, String actor, int current) {
        if (active(type, lineId).isEmpty() && current > 0)
            insert("_legacy_", "legacy-" + lineId, "legacy-" + lineId, type, targetId, lineId, actor, sku, current, "LEGACY");
    }
    public ProcurementContribution insert(String draftId, String batchId, String sourceRow, String type,
            String targetId, String lineId, String owner, String sku, int quantity, String origin) {
        var value = new ProcurementContribution(); value.setId(UUID.randomUUID().toString()); value.setTenantId(tenant());
        value.setDraftId(draftId); value.setBatchId(batchId); value.setSourceRowId(sourceRow); value.setTargetType(type);
        value.setTargetId(targetId); value.setTargetLineId(lineId); value.setOwnerUserId(owner); value.setSkuId(sku);
        value.setQuantity(quantity); value.setStatus("ACTIVE"); value.setOrigin(origin); value.setRevision(0L);
        value.setCreateBy(owner); value.setUpdateBy(owner); value.setDelFlag("0");
        value.setCreateTime(LocalDateTime.now()); value.setUpdateTime(value.getCreateTime()); mapper.insert(value); return value;
    }
    public void revoke(ProcurementContribution contribution) {
        int changed = mapper.update(null, Wrappers.<ProcurementContribution>lambdaUpdate()
            .eq(ProcurementContribution::getTenantId, tenant()).eq(ProcurementContribution::getId, contribution.getId())
            .eq(ProcurementContribution::getStatus, "ACTIVE").set(ProcurementContribution::getStatus, "REVOKED")
            .set(ProcurementContribution::getUpdateTime, LocalDateTime.now()).setSql("revision=revision+1"));
        if (changed != 1) throw new ArynBusinessException(409, "来源贡献已变化");
    }
    /** 手工累计量保留为增量贡献；绝对改量使既有来源冲突，避免猜测拆分旧批次数量。 */
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public void manualShared(String cart, String line, String sku, String actor, int before, int after, boolean incremental) {
        var active = active("SHARED_CART", line); if (active.isEmpty()) return;
        if (incremental) {
            requireConsistent("SHARED_CART", line, before);
            String request = UUID.randomUUID().toString();
            insert("_manual_", request, request, "SHARED_CART", cart, line, actor, sku, Math.subtractExact(after, before), "MANUAL");
        } else {
            for (var contribution : active) mapper.update(null, Wrappers.<ProcurementContribution>lambdaUpdate()
                .eq(ProcurementContribution::getTenantId, tenant()).eq(ProcurementContribution::getId, contribution.getId())
                .set(ProcurementContribution::getStatus, "MANUAL_CONFLICT").setSql("revision=revision+1"));
            if (after > 0) {
                String request = UUID.randomUUID().toString();
                insert("_manual_", request, request, "SHARED_CART", cart, line, actor, sku, after, "MANUAL");
            }
        }
    }
    private String tenant() {
        String tenant = ArynTenantContextHolder.getTenantId();
        if (tenant == null || tenant.isBlank()) throw new ArynBusinessException(403, "缺少租户上下文"); return tenant;
    }
}
