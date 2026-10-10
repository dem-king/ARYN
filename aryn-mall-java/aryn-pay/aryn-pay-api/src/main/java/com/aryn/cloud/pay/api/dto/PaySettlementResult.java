package com.aryn.cloud.pay.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 支付结果核对结果
 *
 * @author 雨滴kian
 */
@Data
@Schema(description = "支付结果核对结果")
public class PaySettlementResult implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "渠道是否已确认该笔支付成功（含此前已由回调落账）")
	private boolean paid;

	@Schema(description = "渠道订单号，paid=true 时下发，供业务侧留档")
	private String channelOrderNo;

	@Schema(description = "渠道交易状态（微信 tradeState：SUCCESS/NOTPAY/USERPAYING/CLOSED/REVOKED/PAYERROR）。"
			+ "业务侧据此给出准确文案：USERPAYING/NOTPAY 为确认中，CLOSED/REVOKED/PAYERROR 为未完成可重新支付")
	private String tradeState;

	public static PaySettlementResult unpaid() {
		return new PaySettlementResult();
	}

	public static PaySettlementResult unpaid(String tradeState) {
		PaySettlementResult result = new PaySettlementResult();
		result.setTradeState(tradeState);
		return result;
	}

	public static PaySettlementResult paid(String channelOrderNo) {
		PaySettlementResult result = new PaySettlementResult();
		result.setPaid(true);
		result.setChannelOrderNo(channelOrderNo);
		result.setTradeState("SUCCESS");
		return result;
	}

}
