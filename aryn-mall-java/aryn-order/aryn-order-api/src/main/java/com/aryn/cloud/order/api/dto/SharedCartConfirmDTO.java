package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 共享购物车确认提交 DTO。
 *
 * <p>确认人可在此调整各明细核定数量后统一提交，生成一个整船订单；
 * 明细保留成员来源和备注。提交以购物车维度幂等（重复确认返回原订单）。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@Schema(description = "共享购物车确认提交DTO")
public class SharedCartConfirmDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "核定数量调整（可省略，默认取成员申请数量）")
	@Valid
	private List<ApprovedQuantity> approvedQuantities;

	@Schema(description = "收货人姓名（内部配送）")
	private String recipientName;

	@Schema(description = "收货人电话（内部配送）")
	private String recipientPhone;

	@Schema(description = "船上代理/经办人姓名")
	private String agentName;

	@Schema(description = "船上代理/经办人电话")
	private String agentPhone;

	@Schema(description = "整单备注")
	private String remark;

	@Data
	@Schema(description = "核定数量调整项")
	public static class ApprovedQuantity implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "明细ID")
		private String itemId;

		@Schema(description = "核定数量（采购单位）")
		private Integer quantity;

	}

}
