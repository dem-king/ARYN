package com.aryn.cloud.promotion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 小程序销售工作台业绩统计
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "小程序销售工作台业绩统计")
public class SalesWorkbenchStatsVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "本月成交单数")
	private Integer monthOrderCount;

	@Schema(description = "本月成交额")
	private BigDecimal monthTradeAmount;

	@Schema(description = "本月预估提成")
	private BigDecimal monthCommission;

	@Schema(description = "累计已产生提成")
	private BigDecimal totalCommission;

}
