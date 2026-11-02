package com.aryn.cloud.upms.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 当前登录用户的个人信息修改请求。
 *
 * <p>不同接口只读取各自需要的字段：基本资料用 nickname/email，修改登录账号用
 * username/password，修改密码用 password/newPassword/checkPassword。
 * 用户 id 一律取自当前会话，不接受客户端传入。</p>
 *
 * @author aryn
 * @since 2026/10/29
 */
@Data
@Schema(description = "个人信息修改请求")
public class SysUserProfileDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "昵称")
	private String nickname;

	@Schema(description = "邮箱")
	private String email;

	@Schema(description = "新的登录账号")
	private String username;

	@Schema(description = "当前密码（修改登录账号或密码时校验）")
	private String password;

	@Schema(description = "新密码")
	private String newPassword;

	@Schema(description = "确认新密码")
	private String checkPassword;

}
