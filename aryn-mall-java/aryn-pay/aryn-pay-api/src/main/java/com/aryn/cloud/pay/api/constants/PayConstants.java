
package com.aryn.cloud.pay.api.constants;

/**
 * 支付常量
 *
 * @author 雨滴kian
 * @since 2022/3/7 14:01
 */
public interface PayConstants {

	String WX_JSAPI_PAY = "WX_JSAPI_PAY";

	String WX_H5_PAY = "WX_H5_PAY";

	String WX_APP_PAY = "WX_APP_PAY";

	String ALI_JSAPI_PAY = "ALI_JSAPI_PAY";

	String ALI_H5_PAY = "ALI_H5_PAY";

	String ALI_APP_PAY = "ALI_APP_PAY";

	/** 支付类型：1.微信；2.支付宝; */
	String PAY_TYPE_0 = "0";

	String PAY_TYPE_1 = "1";

	String PAY_TYPE_2 = "2";

	String CURRENCY = "CNY";

	String PAY_NOTIFY_TYPE = "1";

	String REFUND_NOTIFY_TYPE = "2";

	String WECHAT_REFUND = "WECHAT_REFUND";

	String ALIPAY_REFUND = "ALIPAY_REFUND";

	String FREE_REFUND = "FREE_REFUND";

	String EXTRA_PARAMS = "extraParams";

	String EXTRA_PARAMS_PAY_TYPE = "payType";

	String OUT_TRADE_NO = "outTradeNo";

	String CHANNEL_ORDER_NO = "channelOrderNo";

	String PAY_SUCCESS_TIME = "paySuccessTime";

	String REFUND_TRADE_NO = "refundTradeMo";

	String ALIPAY_SUCCESS = "success";

	String ALIPAY_FAIL = "fail";

	String TENANT_ID = "tenant_id";

}
