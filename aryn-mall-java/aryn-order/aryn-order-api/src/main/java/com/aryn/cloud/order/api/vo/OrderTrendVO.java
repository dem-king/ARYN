package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单交易趋势 VO
 */
@Data
@Schema(description = "订单交易趋势数据")
public class OrderTrendVO {

	@Schema(description = "时间点 (小时 或 日期)")
	private String timePoint;

	@Schema(description = "成交额 (GMV)")
	private BigDecimal gmv;

	@Schema(description = "支付订单数")
	private Integer payOrderCount;

	@Schema(description = "支付买家数")
	private Integer payBuyerCount;

}
