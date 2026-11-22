package com.aryn.cloud.order.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/** 只接受管理员明确的窗口/截单，不从ETA猜测提前小时数。 */
public final class DeliveryGroupCommands {
    private DeliveryGroupCommands() { }
    public record Create(String requestId, String warehouseId, String vesselCallId, Integer windowSequence,
            LocalDateTime windowStart, LocalDateTime windowEnd, LocalDateTime cutoff, Integer maxOrders,
            String reason) implements Serializable { }
    public record Change(String requestId, long expectedRevision, String action, List<String> orderIds,
            LocalDateTime windowStart, LocalDateTime windowEnd, LocalDateTime cutoff, Integer maxOrders,
            boolean allowLate, String reason) implements Serializable { }
    public record Receipt(String groupId, String status, long revision, List<String> affectedOrderIds) implements Serializable { }
    /** 买家只读取自己的订单及公共排程，不返回组内成员、数量、其他订单或操作记录。 */
    public record BuyerSchedule(String orderId, String groupId, String status, LocalDateTime windowStart,
            LocalDateTime windowEnd, LocalDateTime cutoff, boolean needsScheduling) implements Serializable { }
    public record CheckoutSchedule(String vesselId, String vesselCallId, String status, LocalDateTime windowStart,
            LocalDateTime windowEnd, LocalDateTime cutoff, boolean needsScheduling) implements Serializable { }
}
