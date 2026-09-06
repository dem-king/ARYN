
package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 配送员绑定商城账号请求
 *
 * @author aryn
 * @since 2026/9/5
 */
@Data
@Schema(description = "配送员绑定商城账号请求")
public class DeliveryBindingDTO {

	@Schema(description = "商城用户ID")
	@NotEmpty(message = "请选择商城用户")
	private String mallUserId;

}
