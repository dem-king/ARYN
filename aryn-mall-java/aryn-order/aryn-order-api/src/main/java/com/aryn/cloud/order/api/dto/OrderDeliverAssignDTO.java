
package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 订单发货并派单（商城配送/内部配送订单，无需物流单号）
 *
 * @author aryn
 * @since 2026/10/3
 */
@Data
public class OrderDeliverAssignDTO {

	@Schema(description = "订单ID")
	@NotEmpty(message = "订单ID不能为空")
	private String orderId;

	@Schema(description = "配送员ID")
	@NotBlank(message = "配送员不能为空")
	private String staffId;

}
