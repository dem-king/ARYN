package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 销售业绩趋势行（按月份）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "销售业绩趋势行")
public class SalesTrendVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "月份（yyyy-MM）")
	private String month;

	@Schema(description = "单数")
	private Integer orderCount;

	@Schema(description = "成交额")
	private BigDecimal tradeAmount;

	@Schema(description = "提成")
	private BigDecimal commissionAmount;

}
