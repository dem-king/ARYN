
package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "订单消费者DTO")
public class OrderConsumerDTO {

	@Schema(description = "订单ID")
	private String orderId;

	@Schema(description = "租户id")
	private String tenantId;

	@Schema(description = "退款流水号")
	private String refundTradeNo;

	@Schema(description = "订单单号")
	private String orderNo;

}
