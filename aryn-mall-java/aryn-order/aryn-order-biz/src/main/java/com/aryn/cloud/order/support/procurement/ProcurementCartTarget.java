package com.aryn.cloud.order.support.procurement;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.*;
import com.aryn.cloud.order.mapper.*;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;

/** 确认事务内锁定目标并重新检查原有共享车权限，不把草稿权限当成整车确认权。 */
@Component
@RequiredArgsConstructor
public class ProcurementCartTarget {
    private final SharedCartMapper carts;
    private final SharedCartItemMapper items;
    private final SharedCartMemberMapper members;
    public record Owner(String userId, String label) { }
    public SharedCart lock(String tenant, String actor, String id, ProcurementDraft draft) {
        var cart = carts.selectForUpdate(tenant, id);
        if (cart == null || !Set.of(SharedCart.STATUS_DRAFT, SharedCart.STATUS_COLLECTING, SharedCart.STATUS_WAITING_CONFIRM).contains(cart.getStatus())
                || cart.getExpiresAt() != null && !cart.getExpiresAt().isAfter(LocalDateTime.now())) throw new ArynBusinessException(409, "目标共享购物车已不可编辑，请另开采购");
        if ("2".equals(draft.getPurchaseScene())) {
            if (!SharedCart.CART_TYPE_SHIP.equals(cart.getCartType()) || !Objects.equals(draft.getVesselId(), cart.getVesselId())
                    || !Objects.equals(draft.getVesselCallId(), cart.getVesselCallId())) throw new ArynBusinessException(403, "草稿与共享车船舶靠港不一致");
        } else if ("3".equals(draft.getPurchaseScene())) {
            if (!SharedCart.CART_TYPE_SHORE.equals(cart.getCartType()) || !Objects.equals(draft.getBuyerOrgId(), cart.getBuyerOrgId()))
                throw new ArynBusinessException(403, "草稿与共享车企业主体不一致");
        } else throw new ArynBusinessException("个人采购请确认至个人结算草稿");
        var member = members.selectOne(Wrappers.<SharedCartMember>lambdaQuery().eq(SharedCartMember::getTenantId, tenant)
            .eq(SharedCartMember::getCartId, id).eq(SharedCartMember::getUserId, actor));
        if (member == null || member.getDisplayName() == null || member.getDisplayName().isBlank()
                || !actor.equals(cart.getOwnerUserId()) && (!"1".equals(member.getCanEdit())
                || !"1".equals(member.getCanConfirm()) && !actor.equals(cart.getConfirmerUserId())))
            throw new ArynBusinessException(403, "请由已填写姓名且可编辑、可确认的成员并入采购");
        return cart;
    }
    public Owner owner(String tenant, String actor, String cartId, String rawOwner) {
        if (rawOwner == null || rawOwner.isBlank()) return new Owner(actor, "");
        if (rawOwner.length() > 64) throw new ArynBusinessException("归属姓名超过64字，请先核对");
        var matching = members.selectList(Wrappers.<SharedCartMember>lambdaQuery().eq(SharedCartMember::getTenantId, tenant)
            .eq(SharedCartMember::getCartId, cartId).eq(SharedCartMember::getDisplayName, rawOwner.trim()));
        return matching.size() == 1 ? new Owner(matching.get(0).getUserId(), "") : new Owner(actor, rawOwner.trim());
    }
    public SharedCartItem line(String tenant, String cart, Owner owner, String sku, String spu) {
        var item = items.selectOne(Wrappers.<SharedCartItem>lambdaQuery().eq(SharedCartItem::getTenantId, tenant)
            .eq(SharedCartItem::getCartId, cart).eq(SharedCartItem::getUserId, owner.userId()).eq(SharedCartItem::getAttributedName, owner.label())
            .eq(SharedCartItem::getSkuId, sku).last("FOR UPDATE"));
        if (item != null) {
            if (SharedCartItem.ITEM_REMOVED.equals(item.getStatus())) item.setRequestedQuantity(0);
            if (item.getApprovedQuantity() != null) throw new ArynBusinessException(409, "目标行已有人工核定量，请先人工处理来源冲突");
            return item;
        }
        item = new SharedCartItem(); item.setId(UUID.randomUUID().toString()); item.setTenantId(tenant); item.setCartId(cart);
        item.setUserId(owner.userId()); item.setAttributedName(owner.label()); item.setSkuId(sku); item.setSpuId(spu);
        item.setRequestedQuantity(0); item.setStatus(SharedCartItem.ITEM_PENDING); item.setDelFlag("0");
        item.setCreateBy(owner.userId()); item.setUpdateBy(owner.userId()); item.setCreateTime(LocalDateTime.now()); item.setUpdateTime(item.getCreateTime());
        items.insert(item); return item;
    }
    public SharedCartItem existing(String tenant, String cartId, String lineId) {
        var item = items.selectOne(Wrappers.<SharedCartItem>lambdaQuery().eq(SharedCartItem::getTenantId, tenant)
            .eq(SharedCartItem::getCartId, cartId).eq(SharedCartItem::getId, lineId).last("FOR UPDATE"));
        if (item == null || SharedCartItem.ITEM_REMOVED.equals(item.getStatus()) || item.getApprovedQuantity() != null)
            throw new ArynBusinessException(409, "原来源目标已删除或人工核定，不能自动替换");
        return item;
    }
    public Map<String, Integer> quantities(String tenant, String cartId) {
        var all = items.selectList(Wrappers.<SharedCartItem>lambdaQuery().eq(SharedCartItem::getTenantId, tenant)
            .eq(SharedCartItem::getCartId, cartId).ne(SharedCartItem::getStatus, SharedCartItem.ITEM_REMOVED).last("FOR UPDATE"));
        var result = new LinkedHashMap<String, Integer>();
        for (var item : all) {
            int quantity = item.getApprovedQuantity() == null ? item.getRequestedQuantity() : item.getApprovedQuantity();
            if (quantity > 0) result.merge(item.getSkuId(), quantity, Math::addExact);
        }
        return result;
    }
    public void quantity(SharedCartItem item, int quantity) {
        item.setRequestedQuantity(quantity); item.setStatus(quantity == 0 ? SharedCartItem.ITEM_REMOVED : SharedCartItem.ITEM_PENDING);
        item.setUpdateTime(LocalDateTime.now()); items.updateById(item);
    }
}
