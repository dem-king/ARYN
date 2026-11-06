package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 销售业绩单查询条件
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "销售业绩单查询条件")
public class SalesOrderQueryDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "销售ID")
	private String salesStaffId;

	@Schema(description = "部门ID")
	private String deptId;

	@Schema(description = "状态：0待确认 1可发放 2已发放 3已全额退款")
	private String status;

	@Schema(description = "订单号")
	private String orderNo;

	@Schema(description = "买家会员ID")
	private String buyerUserId;

	@Schema(description = "销售姓名（模糊）")
	private String staffName;

	@Schema(description = "归属销售姓名（订单管理筛选用，模糊）")
	private String salesStaffName;

	@Schema(description = "支付开始时间")
	private LocalDateTime payTimeStart;

	@Schema(description = "支付结束时间")
	private LocalDateTime payTimeEnd;

	@Schema(description = "发放批次")
	private String payoutId;

}
