package com.aryn.cloud.user.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 充值预支付DTO
 *
 * @author 雨滴kian
 */
@Data
@Schema(description = "充值预支付DTO")
public class RechargePrepayDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "充值订单号")
	@NotBlank(message = "充值订单号不能为空")
	private String orderNo;

	@Schema(description = "支付类型：1.微信支付；2.支付宝支付")
	@NotBlank(message = "支付类型不能为空")
	private String paymentType;

	@Schema(description = "交易类型")
	@NotBlank(message = "交易类型不能为空")
	private String tradeType;

	@Schema(description = "同步跳转地址，仅支持http/https")
	private String returnUrl;

	@Schema(description = "用户付款中途退出返回商户网站的地址")
	private String quitUrl;

}
