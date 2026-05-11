package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 订单状态概览 VO
 */
@Data
@Schema(description = "订单状态概览数据")
public class OrderStatusOverviewVO {

	@Schema(description = "待付款数量")
	private Integer waitingForPaymentCount;

	@Schema(description = "待发货数量")
	private Integer waitingForDeliveryCount;

	@Schema(description = "已发货数量")
	private Integer shippedCount;

	@Schema(description = "已完成数量")
	private Integer completedCount;

	@Schema(description = "售后中数量")
	private Integer afterSalesCount;

	@Schema(description = "已退款数量")
	private Integer refundedCount;

}
