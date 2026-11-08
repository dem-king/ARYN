package com.aryn.cloud.pay.api.enums;

/**
 * 预支付并发门禁结果。
 */
public enum PayPrepayGateResult {

	/** 已取得提交权，可以调用渠道 */
	STARTED,

	/** 订单已支付，不再调起收银台 */
	ALREADY_PAID,

	/** 另一请求正在提交/关单，当前请求应提示稍后查询 */
	IN_PROGRESS,

	/** 支付单已关闭，需重新下单 */
	CLOSED

}
