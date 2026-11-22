package com.aryn.cloud.order.support;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.DeliveryGroup;
import com.aryn.cloud.order.api.entity.OrderInfo;
import java.time.LocalDateTime;
import java.util.Objects;

/** 同船不等于同靠港，同业务组不等于合并支付或代签授权。 */
public final class DeliveryGroupRules {
    private DeliveryGroupRules() { }
    public static void schedule(LocalDateTime start, LocalDateTime end, LocalDateTime cutoff) {
        if (start == null && end == null && cutoff == null) return;
        if (start == null || end == null || cutoff == null || !start.isBefore(end) || cutoff.isAfter(start))
            throw new ArynBusinessException("请同时设置合法窗口和不晚于窗口开始的截单时间");
    }
    public static void order(DeliveryGroup group, OrderInfo order, LocalDateTime now, boolean allowLate) {
        if (order == null || !Objects.equals(group.getTenantId(), order.getTenantId()))
            throw new ArynBusinessException(403, "订单不存在");
        if (!"2".equals(order.getStatus()) || !"4".equals(order.getDeliveryWay()))
            throw new ArynBusinessException("仅待发货的到船配送订单可归组");
        if (!Objects.equals(group.getVesselId(), order.getVesselId()) || !Objects.equals(group.getVesselCallId(), order.getVesselCallId())
                || !Objects.equals(group.getPortCode(), order.getPortCode()))
            throw new ArynBusinessException("订单与合送组的船舶、靠港或港口不一致");
        if (group.getCutoff() == null || group.getWindowStart() == null || group.getWindowEnd() == null)
            throw new ArynBusinessException(409, "该组待排程，请先明确窗口和截单时间");
        if (!now.isBefore(group.getWindowEnd())) throw new ArynBusinessException(409, "配送窗口已结束，请选择下一窗口或单独配送");
        if (!"OPEN".equals(group.getStatus()) && !(allowLate && "CLOSED".equals(group.getStatus())))
            throw new ArynBusinessException(409, "该组已开始执行或结束，不能加单");
        if ((!now.isBefore(group.getCutoff()) || "CLOSED".equals(group.getStatus())) && !allowLate)
            throw new ArynBusinessException(409, "已截单，请进入下一窗口或单独配送；特批须明确运力和原因");
        if (order.getDeliveryWindowStart() == null || order.getDeliveryWindowEnd() == null
                || !order.getDeliveryWindowStart().isBefore(group.getWindowEnd())
                || !order.getDeliveryWindowEnd().isAfter(group.getWindowStart()))
            throw new ArynBusinessException(409, "订单配送窗口与合送窗口不兼容，请先核对配送安排");
    }
}
