package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单交易统计 VO
 */
@Data
@Schema(description = "订单交易统计数据")
public class OrderTradeStatisticsVO {

	@Schema(description = "成交额 (GMV)")
	private BigDecimal gmv;

	@Schema(description = "支付订单数")
	private Integer payOrderCount;

	@Schema(description = "支付买家数")
	private Integer payBuyerCount;

	@Schema(description = "客单价 (GMV / 支付买家数)")
	private BigDecimal averageTicketSize;

	@Schema(description = "售后订单数")
	private Integer afterSalesOrderCount;

	@Schema(description = "退款金额")
	private BigDecimal refundAmount;

}
