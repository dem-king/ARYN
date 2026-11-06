package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 销售业绩报表行（按销售/部门汇总）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "销售业绩报表行")
public class SalesReportVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "销售ID")
	private String salesStaffId;

	@Schema(description = "销售姓名")
	private String staffName;

	@Schema(description = "部门名称")
	private String deptName;

	@Schema(description = "单数")
	private Integer orderCount;

	@Schema(description = "成交额")
	private BigDecimal tradeAmount;

	@Schema(description = "退款额")
	private BigDecimal refundAmount;

	@Schema(description = "应发提成")
	private BigDecimal commissionAmount;

	@Schema(description = "已发放提成")
	private BigDecimal paidAmount;

}
