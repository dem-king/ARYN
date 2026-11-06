package com.aryn.cloud.promotion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 销售业绩单汇总卡片（管理端 /sales/order/statistics）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "销售业绩单汇总卡片")
public class SalesOrderStatisticsVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "业绩单数")
	private Long orderCount;

	@Schema(description = "成交额（基数-已退基数）")
	private BigDecimal tradeAmount;

	@Schema(description = "应发提成（提成-已扣回）")
	private BigDecimal commissionAmount;

	@Schema(description = "退款额（已退基数）")
	private BigDecimal refundAmount;

}
