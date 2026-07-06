
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

	/** 死信队列监控 topic（用于统一监控各消费者组的DLQ消息） */
	String DLQ_MONITOR_TOPIC = "dlq-monitor-topic";

	/** 商品索引同步 topic */
	String PRODUCT_INDEX_SYNC_TOPIC = "product-index-sync-topic";

	/** 发送消息超时时间 */
	long TIME_OUT = 3000;

	/** 订单超时取消等级 30分钟 */
	int ORDER_CANCEL_LEVEL = 16;

	/** 消费者默认最大重试次数 */
	int DEFAULT_MAX_RECONSUME_TIMES = 3;

}
