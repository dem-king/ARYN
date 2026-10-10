
package com.aryn.cloud.user.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserLoginReqDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String appId;

	/**
	 * 原始请求头 tenant-id（由 Controller 无条件覆盖写入，禁止采信客户端 body）。
	 * 登录前用于 AppID↔租户绑定校验；有 token 时过滤器会改用会话租户，
	 * 因此不能从 ThreadLocal 取值代替该字段。
	 */
	private String requestTenantId;

	private String jsCode;

	private String platformType;

	private String phone;

	private String password;

	private String code;

	private String wxUserId;

}
