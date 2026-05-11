package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 分销归因结算请求
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
@Data
@Schema(description = "分销归因结算请求")
public class DistributionSettleDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@NotBlank(message = "订单ID不能为空")
	@Schema(description = "订单ID")
	private String orderId;

	@NotBlank(message = "买家ID不能为空")
	@Schema(description = "买家用户ID")
	private String buyerUserId;

	@NotNull(message = "订单金额不能为空")
	@Schema(description = "订单金额")
	private BigDecimal orderAmount;

}
