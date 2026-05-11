/** 
  * Copyright (c) 2025 天启雨数科技有限公司 
  * All rights reserved. 
  * <p> 
  * 注意： 
  * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。 
 
  */
package com.aryn.cloud.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户DTO
 *
 * @author 雨滴kian
 * @date 2022/6/10
 */
@Data
@Schema(description = "用户DTO")
public class UserInfoDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "appId")
	private String appId;

	@Schema(description = "用户主键")
	private String id;

	@Schema(description = "手机号")
	private String phone;

	@Schema(description = "密码")
	private String password;

	@Schema(description = "确认密码")
	private String confirmPassword;

	@Schema(description = "验证码")
	private String code;

}
