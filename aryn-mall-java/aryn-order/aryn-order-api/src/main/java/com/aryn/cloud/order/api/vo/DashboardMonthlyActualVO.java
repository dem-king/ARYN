package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 月度实际成交指标。
 */
@Data
@Schema(description = "月度实际成交指标")
public class DashboardMonthlyActualVO {

	@Schema(description = "GMV")
	private BigDecimal gmv;

	@Schema(description = "支付订单数")
	private Integer orderCount;

}
