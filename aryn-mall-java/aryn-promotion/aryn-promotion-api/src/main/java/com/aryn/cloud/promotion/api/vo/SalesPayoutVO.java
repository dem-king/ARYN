package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售提成月度发放台账视图
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "销售提成月度发放台账视图")
public class SalesPayoutVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private String id;

	@Schema(description = "销售ID")
	private String salesStaffId;

	@Schema(description = "销售姓名")
	private String staffName;

	@Schema(description = "部门ID")
	private String deptId;

	@Schema(description = "发放月份（yyyy-MM）")
	private String payoutMonth;

	@Schema(description = "业绩单数")
	private Integer orderCount;

	@Schema(description = "成交额")
	private BigDecimal tradeAmount;

	@Schema(description = "应发提成")
	private BigDecimal commissionAmount;

	@Schema(description = "上期退款冲减")
	private BigDecimal refundDeductAmount;

	@Schema(description = "实发金额")
	private BigDecimal payableAmount;

	@Schema(description = "状态：0待确认 1已发放")
	private String status;

	@Schema(description = "确认人")
	private String confirmBy;

	@Schema(description = "确认发放时间")
	private LocalDateTime confirmTime;

	@Schema(description = "备注/凭证号")
	private String remark;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

}
