package com.aryn.cloud.order.support;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.*;
import com.aryn.cloud.order.mapper.*;
import com.aryn.cloud.order.service.impl.DeliveryGroupService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.time.LocalDateTime;
import java.util.*;

/** 仅在履约事务内使用：订单→合送组→波次→配送任务锁，保留各业务ID。 */
@Component
@RequiredArgsConstructor
public class DeliveryGroupFulfillment {
    private final DeliveryGroupMapper groups;
    private final DeliveryGroupOrderLinkMapper links;
    private final DeliveryWarehouseConfigMapper warehouses;
    private final DeliveryTaskMapper tasks;
    private final DeliveryGroupService service;

    public List<DeliveryGroupOrderLink> lockGroups(String tenant, List<String> orderIds) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("波次归组必须在事务内");
        // 调用方已锁订单；这些订单的归组关系在本事务期间不能被移走。
        var selected = links.selectList(Wrappers.<DeliveryGroupOrderLink>lambdaQuery().eq(DeliveryGroupOrderLink::getTenantId, tenant)
            .in(DeliveryGroupOrderLink::getOrderId, orderIds).eq(DeliveryGroupOrderLink::getStatus, "ACTIVE"));
        for (var id : selected.stream().map(DeliveryGroupOrderLink::getGroupId).distinct().sorted().toList()) {
            var group = groups.selectOne(Wrappers.<DeliveryGroup>lambdaQuery().eq(DeliveryGroup::getTenantId, tenant).eq(DeliveryGroup::getId, id).last("FOR UPDATE"));
            if (group == null || !Set.of("OPEN", "CLOSED", "FULFILLING").contains(group.getStatus())) throw new ArynBusinessException(409, "订单合送组不可执行");
        }
        return selected;
    }
    public void validateWarehouse(String tenant, FulfillmentWave wave) {
        if (wave.getWarehouseId() == null || wave.getWarehouseId().isBlank() || warehouses.selectOne(Wrappers.<DeliveryWarehouseConfig>lambdaQuery()
                .eq(DeliveryWarehouseConfig::getTenantId, tenant).eq(DeliveryWarehouseConfig::getId, wave.getWarehouseId())) == null)
            throw new ArynBusinessException("请选择当前租户的有效仓库");
    }
    public void bind(String tenant, OrderInfo order, FulfillmentWave wave, List<DeliveryGroupOrderLink> associations) {
        if (!Objects.equals(order.getVesselCallId(), wave.getVesselCallId())) throw new ArynBusinessException("订单与波次靠港不一致");
        if (order.getFulfillmentWarehouseId() != null && !wave.getWarehouseId().equals(order.getFulfillmentWarehouseId()))
            throw new ArynBusinessException("订单已核定其它仓库");
        var task = tasks.selectOne(Wrappers.<DeliveryTask>lambdaQuery().eq(DeliveryTask::getTenantId, tenant).eq(DeliveryTask::getOrderId, order.getId()).last("FOR UPDATE"));
        if (task != null && (task.getWarehouseId() != null && !wave.getWarehouseId().equals(task.getWarehouseId()) || !"1".equals(task.getStatus())))
            throw new ArynBusinessException(409, "订单配送任务的仓库或执行状态不允许入波次");
        for (var association : associations) {
            if (!order.getId().equals(association.getOrderId())) continue;
            var group = groups.selectOne(Wrappers.<DeliveryGroup>lambdaQuery().eq(DeliveryGroup::getTenantId, tenant)
                .eq(DeliveryGroup::getId, association.getGroupId()).last("FOR UPDATE"));
            if (!wave.getWarehouseId().equals(group.getWarehouseId()) || !Objects.equals(order.getVesselId(), group.getVesselId())
                    || !Objects.equals(wave.getVesselCallId(), group.getVesselCallId()) || !Objects.equals(wave.getPortCode(), group.getPortCode()))
                throw new ArynBusinessException("合送组与波次的仓库、船舶或靠港不一致");
            if (association.getWaveId() != null && !wave.getId().equals(association.getWaveId())) throw new ArynBusinessException(409, "订单已关联另一波次");
            if (!wave.getId().equals(association.getWaveId())) links.update(null, Wrappers.<DeliveryGroupOrderLink>lambdaUpdate().eq(DeliveryGroupOrderLink::getTenantId, tenant)
                .eq(DeliveryGroupOrderLink::getId, association.getId()).eq(DeliveryGroupOrderLink::getStatus, "ACTIVE")
                .set(DeliveryGroupOrderLink::getWaveId, wave.getId()).set(DeliveryGroupOrderLink::getTaskId, task == null ? null : task.getId())
                .set(DeliveryGroupOrderLink::getRevision, association.getRevision() + 1).set(DeliveryGroupOrderLink::getUpdateTime, LocalDateTime.now()));
        }
        if (task != null) tasks.update(null, Wrappers.<DeliveryTask>lambdaUpdate().eq(DeliveryTask::getTenantId, tenant)
            .eq(DeliveryTask::getId, task.getId()).set(DeliveryTask::getWarehouseId, wave.getWarehouseId()));
    }
    public void start(String tenant, String actor, FulfillmentWave wave, List<DeliveryGroupOrderLink> associations) {
        service.startFulfillment(tenant, actor, associations.stream().map(DeliveryGroupOrderLink::getGroupId).distinct().sorted().toList(), wave.getId());
    }
}
