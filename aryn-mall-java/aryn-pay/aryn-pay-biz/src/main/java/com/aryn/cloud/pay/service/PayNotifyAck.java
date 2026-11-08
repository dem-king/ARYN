package com.aryn.cloud.pay.service;

/**
 * 渠道回调应答。success=false 时控制器必须返回非 2xx，保留渠道重试机会。
 */
public record PayNotifyAck(boolean success, String message) {

	public static PayNotifyAck ok() {
		return new PayNotifyAck(true, null);
	}

	public static PayNotifyAck failure(String message) {
		return new PayNotifyAck(false, message);
	}

}
