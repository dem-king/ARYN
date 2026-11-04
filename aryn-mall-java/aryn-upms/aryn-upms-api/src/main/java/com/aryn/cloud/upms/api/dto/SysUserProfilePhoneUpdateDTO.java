package com.aryn.cloud.upms.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 当前登录用户自助换绑手机号的请求。
 *
 * <p>用户 id 一律取自当前会话，不接受客户端传入；换绑要求当前密码 + 新手机号短信验证码，
 * 避免会话被劫持后直接改掉登录凭据。旧手机号可能已丢失，因此不要求旧号验证码。</p>
 *
 * @author aryn
 * @since 2026/11/2
 */
@Data
@Schema(description = "个人信息手机号换绑请求")
public class SysUserProfilePhoneUpdateDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "新的手机号")
	private String phone;

	@Schema(description = "新手机号短信验证码")
	private String code;

	@Schema(description = "当前密码")
	private String password;

}
