package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 客户归属手动指派/改绑请求
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "客户归属手动指派请求")
public class SalesCustomerAssignDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "客户会员ID")
	@NotBlank(message = "客户不能为空")
	private String customerUserId;

	@Schema(description = "目标销售ID")
	@NotBlank(message = "请选择归属销售")
	private String salesStaffId;

	@Schema(description = "改绑原因（必填）")
	@NotBlank(message = "请填写改绑原因")
	private String reason;

}
