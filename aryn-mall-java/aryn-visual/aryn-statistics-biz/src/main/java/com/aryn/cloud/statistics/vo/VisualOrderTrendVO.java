package com.aryn.cloud.statistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 大屏订单趋势 VO
 */
@Data
@Schema(description = "大屏订单趋势数据")
public class VisualOrderTrendVO {

	@Schema(description = "时间点 (日期)")
	private String timePoint;

	@Schema(description = "成交额 (GMV)")
	private BigDecimal gmv;

	@Schema(description = "支付订单数")
	private Integer payOrderCount;

}