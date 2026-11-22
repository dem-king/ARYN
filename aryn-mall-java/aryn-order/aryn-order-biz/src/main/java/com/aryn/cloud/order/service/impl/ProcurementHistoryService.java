package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.ProcurementCommands.*;
import com.aryn.cloud.order.api.entity.*;
import com.aryn.cloud.order.mapper.*;
import com.aryn.cloud.order.service.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.util.*;

/** 历史只是来源；不直接加购，不继承历史价格、库存或度量事实。 */
@Service
@RequiredArgsConstructor
public class ProcurementHistoryService {
    private final ProcurementDraftService drafts;
    private final IOrderInfoService orders;
    private final ISharedCartService shared;
    private final OrderInfoMapper orderMapper;
    private final OrderItemMapper orderItems;
    private final SharedCartItemMapper cartItems;
    private final SharedCartMemberMapper members;
    private final ObjectMapper json;
    public record HistoryCommand(String clientRequestId, long expectedRevision, String sourceType,
            String sourceId, String skuId, Integer salesQuantity, boolean explicitNew) { }
    public record ReuseCommand(String clientRequestId, String vesselId, String vesselCallId) { }
    public record ReuseResult(String draftId, String batchId, String cartId) { }
    public ReuseResult reuse(String tenant, String actor, String sourceId, ReuseCommand command) {
        if (command == null) throw new ArynBusinessException("缺少历史复用命令");
        ProcurementDraftService.requestId(command.clientRequestId());
        var original = shared.getCartForUser(tenant, actor, sourceId);
        if (Set.of("1", "2", "3").contains(original.getStatus())) throw new ArynBusinessException("进行中补给单无需复用");
        var input = new com.aryn.cloud.order.api.dto.SharedCartCreateDTO();
        boolean shore = "2".equals(original.getCartType());
        input.setCartType(shore ? "2" : "1"); input.setRemark(original.getRemark());
        if (shore) { input.setBuyerOrgId(original.getBuyerOrgId()); input.setUserAddressId(original.getUserAddressId()); }
        else {
            if (!Objects.equals(original.getVesselId(), command.vesselId())) throw new ArynBusinessException(403, "仅能复用到原船舶");
            input.setVesselId(command.vesselId()); input.setVesselCallId(command.vesselCallId());
        }
        // 现有create负责目标资格/靠港和进行中车去重；随后仅保存来源，不写目标明细。
        var target = shared.create(tenant, actor, input);
        var draft = drafts.create(tenant, actor, new CreateDraft(command.clientRequestId(), shore ? "3" : "2", target.getBuyerOrgId(),
            target.getVesselId(), target.getVesselCallId(), "历史补给单复用"));
        var result = submit(tenant, actor, draft.getId(), new HistoryCommand(command.clientRequestId(), draft.getRevision(), "HISTORICAL", sourceId, null, null, false));
        return new ReuseResult(draft.getId(), result.batchId(), target.getId());
    }
    public BatchSubmission submit(String tenant, String actor, String draftId, HistoryCommand command) {
        if (command == null || !Set.of("REORDER", "HISTORICAL", "FREQUENT").contains(Objects.toString(command.sourceType(), "")))
            throw new ArynBusinessException("请选择历史来源类型");
        var draft = drafts.get(tenant, actor, draftId);
        var result = new ArrayList<ProcurementExtractionService.Source>();
        if ("REORDER".equals(command.sourceType())) {
            var original = orderMapper.selectOne(Wrappers.<OrderInfo>lambdaQuery().eq(OrderInfo::getTenantId, tenant)
                .eq(OrderInfo::getUserId, actor).eq(OrderInfo::getId, command.sourceId()));
            if (original == null) throw new ArynBusinessException(403, "无权复用原订单");
            requireContext(draft, original.getPurchaseScene(), original.getBuyerOrgId(), original.getVesselId());
            var items = orderItems.selectList(Wrappers.<OrderItemEntity>lambdaQuery().eq(OrderItemEntity::getTenantId, tenant)
                .eq(OrderItemEntity::getOrderId, original.getId()).orderByAsc(OrderItemEntity::getId).last("LIMIT 2001"));
            for (var item : items) result.add(source(result.size(), item.getSkuId(), item.getSpuName(), item.getSpecsInfo(),
                item.getBuyQuantity(), item.getPurchaseUnit(), item.getContributorName()));
        } else if ("HISTORICAL".equals(command.sourceType())) {
            var original = shared.getCartForUser(tenant, actor, command.sourceId());
            if (Set.of("1", "2", "3").contains(original.getStatus())) throw new ArynBusinessException("进行中补给单无需复用");
            requireContext(draft, "2".equals(original.getCartType()) ? "3" : "2", original.getBuyerOrgId(), original.getVesselId());
            var labels = new HashMap<String, String>();
            members.selectList(Wrappers.<SharedCartMember>lambdaQuery().eq(SharedCartMember::getTenantId, tenant)
                .eq(SharedCartMember::getCartId, original.getId())).forEach(member -> labels.put(member.getUserId(), member.getDisplayName()));
            var items = cartItems.selectList(Wrappers.<SharedCartItem>lambdaQuery().eq(SharedCartItem::getTenantId, tenant)
                .eq(SharedCartItem::getCartId, original.getId()).in(SharedCartItem::getStatus, "1", "2")
                .orderByAsc(SharedCartItem::getId).last("LIMIT 2001"));
            for (var item : items) {
                var quantity = item.getApprovedQuantity() == null ? item.getRequestedQuantity() : item.getApprovedQuantity();
                if (quantity != null && quantity == 0) continue;
                String owner = item.getAttributedName() == null || item.getAttributedName().isBlank() ? labels.get(item.getUserId()) : item.getAttributedName();
                result.add(source(result.size(), item.getSkuId(), null, null, quantity, null, owner));
            }
        } else {
            var item = orders.frequentPurchase(tenant, actor).stream().filter(value -> Objects.equals(command.skuId(), value.getSkuId())).findFirst()
                .orElseThrow(() -> new ArynBusinessException(403, "商品不属于当前用户常购清单"));
            if (command.salesQuantity() != null && command.salesQuantity() < 1) throw new ArynBusinessException("采购数量须为正整数");
            result.add(source(0, item.getSkuId(), item.getSpuName(), item.getSpecsInfo(), command.salesQuantity(), null, null));
        }
        if (result.isEmpty() || result.size() > 2000) throw new ArynBusinessException("历史来源须为1至2000行");
        try {
            String payload = json.writeValueAsString(Map.of("sourceType", command.sourceType(), "sourceId", Objects.toString(command.sourceId(), command.skuId()), "rows", result));
            return drafts.submitHistory(tenant, actor, draftId, command.clientRequestId(), command.expectedRevision(), command.explicitNew(), command.sourceType(), payload);
        } catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalStateException(invalid); }
    }
    private ProcurementExtractionService.Source source(int index, String sku, String name, String spec, Integer quantity, String unit, String owner) {
        return new ProcurementExtractionService.Source(String.format(Locale.ROOT, "h:%05d", index), "HISTORY", index + 1, 0,
            Objects.toString(name, sku) + " | " + Objects.toString(spec, "") + " | " + Objects.toString(quantity, "?") + " " + Objects.toString(unit, "?"),
            name, quantity == null ? null : BigDecimal.valueOf(quantity), unit, spec, sku, "INTERNAL_SKU", owner,
            "HISTORICAL_MEASUREMENT_REQUIRES_REVIEW", false);
    }
    private void requireContext(ProcurementDraft draft, String scene, String buyer, String vessel) {
        if (!Objects.equals(Objects.toString(scene, "1"), draft.getPurchaseScene())
                || !Objects.equals(blank(buyer), blank(draft.getBuyerOrgId()))
                || !Objects.equals(blank(vessel), blank(draft.getVesselId())))
            throw new ArynBusinessException(403, "历史来源与当前采购主体或船舶不一致，请重新选择采购上下文");
    }
    private String blank(String value) { return value == null || value.isBlank() ? null : value; }
}
