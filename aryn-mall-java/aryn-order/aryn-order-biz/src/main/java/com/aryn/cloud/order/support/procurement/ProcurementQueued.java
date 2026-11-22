package com.aryn.cloud.order.support.procurement;
/** 仓储提交返回后发布；调度扫描负责恢复漏唤醒。 */
public record ProcurementQueued(String tenantId) { }
