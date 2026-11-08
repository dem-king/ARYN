package com.aryn.cloud.pay.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 支付宝小程序授权码交换请求。
 *
 * <p>tenantId/appId/identityMode 均由服务端调用方在完成租户绑定校验后传入，
 * 不接受客户端直接透传。
 */
@Data
@Schema(description = "支付宝小程序授权码交换请求")
public class AlipayAuthCodeExchangeReqDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@NotBlank(message = "租户不能为空")
	private String tenantId;

	@NotBlank(message = "应用ID不能为空")
	private String appId;

	@NotBlank(message = "授权码不能为空")
	private String authCode;

	@NotBlank(message = "身份模式不能为空")
	private String identityMode;

}
