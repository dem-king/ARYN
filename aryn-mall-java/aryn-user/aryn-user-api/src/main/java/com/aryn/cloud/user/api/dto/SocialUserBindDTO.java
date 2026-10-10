package com.aryn.cloud.user.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class SocialUserBindDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String id;

	private String mallUserId;

	private String appId;

	/**
	 * 已验证租户（登录 guard 校验通过后的租户）：绑定侧用它核对三方账号归属，
	 * 防止主键跨租户反查把账号绑到其它租户。
	 */
	private String expectedTenantId;

}
