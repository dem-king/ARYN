
package com.aryn.cloud.pay.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class CreateRefundsReqDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "商户订单号")
	private String outTradeNo;

	@Schema(description = "商户退款单号")
	private String refundTradeNo;

	@Schema(description = "订单总金额")
	private BigDecimal totalAmount;

	@Schema(description = "退款金额")
	private BigDecimal refundAmount;

	@Schema(description = "通知地址")
	private String notifyUrl;

	@Schema(description = "退款类型；WEICHAT_REFUND.微信退款；ALIPAY_REFUND.支付宝退款；")
	private String refundType;

	@Schema(description = "额外参数")
	private String extra;

	private String userId;

}
