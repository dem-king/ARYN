package com.aryn.cloud.pay.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 取消前支付门禁结果。
 */
@Data
@Schema(description = "支付关单核对结果")
public class PayCloseResult implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public static final String STATUS_PAID = "PAID";

	public static final String STATUS_CLOSED = "CLOSED";

	public static final String STATUS_UNKNOWN = "UNKNOWN";

	public static final String STATUS_NOT_INITIATED = "NOT_INITIATED";

	@Schema(description = "PAID/CLOSED/UNKNOWN/NOT_INITIATED")
	private String status;

	@Schema(description = "渠道交易状态；可能为空")
	private String tradeState;

	@Schema(description = "面向业务的说明")
	private String message;

	public static PayCloseResult of(String status, String tradeState, String message) {
		PayCloseResult result = new PayCloseResult();
		result.setStatus(status);
		result.setTradeState(tradeState);
		result.setMessage(message);
		return result;
	}

	public boolean paid() {
		return STATUS_PAID.equals(status);
	}

	public boolean closedOrNotInitiated() {
		return STATUS_CLOSED.equals(status) || STATUS_NOT_INITIATED.equals(status);
	}

	public boolean unknown() {
		return STATUS_UNKNOWN.equals(status);
	}

}
