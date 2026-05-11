
package com.aryn.cloud.common.core.constant;

/**
 * RocketMq常量
 *
 * @author 雨滴kian
 * @date 2022/8/6
 */
public interface RocketMqConstants {

	/** 订单取消 topic */
	String ORDER_CANCEL_TOPIC = "order-cancel-topic";

	/** 支付通知topic */
	String PAY_NOTIFY_TOPIC = "pay-notify-topic";

	/** 订单支付通知topic */
	String ORDER_PAY_SUCCESS_NOTIFY_TOPIC = "order-pay-success-notify-topic";

	/** 订单退款通知topic */
	String ORDER_REFUND_SUCCESS_NOTIFY_TOPIC = "order-refund-success-notify-topic";

	/** 订单完成通知topic */
	String ORDER_COMPLETE_NOTIFY_TOPIC = "order-complete-notify-topic";

	/** 退款通知topic */
	String PAY_REFUND_NOTIFY_TOPIC = "pay-refund-notify-topic";

	/** 发送消息超时时间 */
	long TIME_OUT = 3000;

	/** 订单超时取消等级 30分钟 */
	int ORDER_CANCEL_LEVEL = 16;

}
