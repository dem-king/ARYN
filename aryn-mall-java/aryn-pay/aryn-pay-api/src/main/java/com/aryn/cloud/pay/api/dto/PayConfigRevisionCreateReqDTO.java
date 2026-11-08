
package com.aryn.cloud.pay.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 创建支付身份版本（草稿）。草稿允许材料不齐，启用时再校验。
 */
@Data
@Schema(description = "创建支付身份版本")
public class PayConfigRevisionCreateReqDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@NotBlank(message = "应用ID不能为空")
	private String appId;

	@NotBlank(message = "商户号不能为空")
	private String mchId;

	@Schema(description = "环境：prod/sandbox；微信仅支持 prod")
	private String environment = "prod";

	@Schema(description = "微信支付公钥ID（PUB_KEY_ID_...）")
	private String wxPublicKeyId;

	/**
	 * 支付宝小程序买家身份模式：user_id/open_id；非支付宝小程序必须为空。
	 */
	@Schema(description = "支付宝小程序买家身份模式：user_id/open_id")
	private String buyerIdentityMode;

	@Valid
	private List<PayConfigMaterialRefDTO> materials = new ArrayList<>();

}
