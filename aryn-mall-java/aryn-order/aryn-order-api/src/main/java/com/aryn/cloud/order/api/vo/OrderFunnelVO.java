package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 按下单时间统计的订单履约漏斗。
 */
@Data
@Schema(description = "订单履约漏斗")
public class OrderFunnelVO {

	@Schema(description = "下单订单数")
	private Long createdCount;

	@Schema(description = "支付订单数")
	private Long paidCount;

	@Schema(description = "发货订单数")
	private Long deliveredCount;

	@Schema(description = "完成订单数")
	private Long completedCount;

	@Schema(description = "退款订单数")
	private Long refundedCount;

}
