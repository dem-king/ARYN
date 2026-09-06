
package com.aryn.cloud.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 配送员账号密码登录请求
 *
 * @author aryn
 * @since 2026/9/5
 */
@Data
@Schema(description = "配送员账号密码登录请求")
public class DeliveryLoginRequest implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "员工账号手机号")
	@NotBlank(message = "手机号不能为空")
	@Pattern(regexp = "^1\\d{10}$", message = "手机号格式不正确")
	private String phone;

	@Schema(description = "登录密码")
	@NotBlank(message = "密码不能为空")
	private String password;

}
