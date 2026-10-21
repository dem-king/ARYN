package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 退款侧汇总指标。
 */
@Data
@Schema(description = "退款侧汇总指标")
public class OrderRefundSummaryVO {

	@Schema(description = "售后订单数")
	private Integer afterSalesOrderCount;

	@Schema(description = "退款金额")
	private BigDecimal refundAmount;

}
