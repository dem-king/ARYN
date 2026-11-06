package com.aryn.cloud.promotion.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 发放台账聚合行（按销售聚合待发放业绩单）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "发放台账聚合行")
public class SalesPayoutAggregateVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "销售ID")
	private String salesStaffId;

	@Schema(description = "销售姓名")
	private String staffName;

	@Schema(description = "部门ID")
	private String deptId;

	@Schema(description = "业绩单数")
	private Integer orderCount;

	@Schema(description = "成交额（基数-已退基数）")
	private BigDecimal tradeAmount;

	@Schema(description = "应发提成（提成-已扣回）")
	private BigDecimal commissionAmount;

}
