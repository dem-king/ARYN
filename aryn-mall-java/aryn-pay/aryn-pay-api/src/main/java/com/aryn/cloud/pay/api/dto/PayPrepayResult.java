
package com.aryn.cloud.pay.api.dto;

import com.aryn.cloud.pay.api.enums.PayInvokeTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 预支付统一契约。C 端不得直接消费渠道 SDK Response。
 */
@Data
@Schema(description = "预支付结果")
public class PayPrepayResult implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String channel;

	private String terminalType;

	private PayInvokeTypeEnum invokeType;

	private String orderNo;

	private boolean alreadyPaid;

	private Map<String, Object> params = new LinkedHashMap<>();

	private String message;

	public static PayPrepayResult alreadyPaid(String orderNo) {
		PayPrepayResult result = new PayPrepayResult();
		result.setOrderNo(orderNo);
		result.setInvokeType(PayInvokeTypeEnum.ALREADY_PAID);
		result.setAlreadyPaid(true);
		result.setMessage("订单已支付，请以服务端订单状态为准");
		return result;
	}

	public static PayPrepayResult of(String channel, String terminalType, PayInvokeTypeEnum invokeType, String orderNo,
			Map<String, Object> params) {
		PayPrepayResult result = new PayPrepayResult();
		result.setChannel(channel);
		result.setTerminalType(terminalType);
		result.setInvokeType(invokeType);
		result.setOrderNo(orderNo);
		result.setParams(params == null ? new LinkedHashMap<>() : new LinkedHashMap<>(params));
		return result;
	}

}
