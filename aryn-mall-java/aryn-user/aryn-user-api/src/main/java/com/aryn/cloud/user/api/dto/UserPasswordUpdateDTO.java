package com.aryn.cloud.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserPasswordUpdateDTO {

	private String currentPassword;

	@NotBlank(message = "密码不能为空")
	@Size(min = 8, max = 64, message = "密码长度必须为8到64个字符")
	private String password;

	@NotBlank(message = "确认密码不能为空")
	private String confirmPassword;

}
