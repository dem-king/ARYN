package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 日汇总对账的原始/汇总侧指标。
 */
@Data
@Schema(description = "日汇总对账指标")
public class DailyReconcileRawVO {

	@Schema(description = "GMV")
	private BigDecimal gmv;

	@Schema(description = "支付订单数")
	private Integer orderCount;

	@Schema(description = "支付买家数")
	private Integer buyerCount;

}
