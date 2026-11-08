package com.aryn.cloud.pay.api.enums;

/**
 * 支付单生命周期。旧 pay_status 只作为 0/1 兼容投影，不再作为独立状态源。
 */
public enum PayTradeStatusEnum {

	/** 已记录提交意图，正在调用渠道 */
	SUBMITTING,

	/** 渠道已受理预支付，等待付款/回调 */
	PENDING,

	/** 正在向渠道发起关单 */
	CLOSING,

	/** 渠道结果不明/网络失败，等待查单收敛 */
	UNKNOWN,

	/** 渠道确认已关闭/撤销 */
	CLOSED,

	/** 已确认支付成功 */
	PAID;

	public boolean isFinal() {
		return this == CLOSED || this == PAID;
	}

	public static PayTradeStatusEnum fromCode(String code) {
		for (PayTradeStatusEnum status : values()) {
			if (status.name().equals(code)) {
				return status;
			}
		}
		return null;
	}

}
