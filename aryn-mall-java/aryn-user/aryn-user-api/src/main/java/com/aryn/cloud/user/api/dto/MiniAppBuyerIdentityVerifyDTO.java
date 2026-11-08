package com.aryn.cloud.user.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 支付宝小程序买家身份归属校验。
 *
 * <p>支付域在下单/预支付前调用：买家身份必须已绑定到当前租户、应用、身份模式的
 * 商城用户，防止把微信 openid 或其它租户身份写入支付宝买家字段。
 */
@Data
public class MiniAppBuyerIdentityVerifyDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String tenantId;

	private String platformType;

	private String appId;

	private String identityMode;

	private String buyerIdentity;

	private String mallUserId;

}
