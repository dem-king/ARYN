package com.aryn.cloud.pay.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 支付宝小程序买家身份交换结果。
 *
 * <p>identity 严格按配置的 identityMode 取 user_id 或 open_id，两种模式不互相回退；
 * 不返回授权码、access_token 或任何应用凭证。
 */
@Data
@Schema(description = "支付宝小程序买家身份")
public class AlipayMiniIdentityDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String tenantId;

	private String appId;

	private String identityMode;

	/**
	 * 与 identityMode 对应的买家身份值。
	 */
	private String identity;

}
