
package com.aryn.cloud.pay.api.enums;

/**
 * C 端收银台调起方式。前端只能按此枚举消费参数，不能直接序列化渠道 SDK Response。
 */
public enum PayInvokeTypeEnum {

	/** 微信小程序 wx.requestPayment */
	WX_MINI,

	/** 微信 App OpenSDK（Android/iOS 传对象，不传 JSON 字符串） */
	WX_APP,

	/** 支付宝 App 已签名订单串 */
	ALI_APP,

	/** 支付宝小程序 tradeNo → uni.requestPayment */
	ALI_MINI,

	/** 受信任跳转 URL（微信 H5 / 支付宝 H5） */
	REDIRECT,

	/** 业务订单已支付，不再拉起收银台 */
	ALREADY_PAID,

	/** 零元支付等无需调起渠道 */
	NONE

}
