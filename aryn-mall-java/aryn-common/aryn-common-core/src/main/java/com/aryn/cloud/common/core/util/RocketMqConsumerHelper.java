
package com.aryn.cloud.common.core.util;

import org.slf4j.Logger;

/**
 * RocketMQ 消费者统一工具类
 * <p>
 * 封装消费失败补偿逻辑：结构化日志 + 重试次数追踪 + 死信队列告警。
 * 本类不依赖 RocketMQ 客户端，可在 L0 层（common-core）中使用。
 * </p>
 *
 * @author aryn
 * @since 2026/07/05
 */
public final class RocketMqConsumerHelper {

	private RocketMqConsumerHelper() {
	}

	/** 默认最大重试次数 */
	public static final int DEFAULT_MAX_RECONSUME_TIMES = 3;

	/**
	 * 安全执行消费逻辑，自动记录重试与失败日志。
	 * <p>
	 * 当消费失败时抛出异常，由 RocketMQ 框架自动重试；
	 * 达到最大重试次数后消息进入死信队列（%DLQ%consumerGroup）。
	 * </p>
	 *
	 * @param log          调用方的 Slf4j Logger
	 * @param topic        消息主题
	 * @param consumerGroup 消费者组
	 * @param message      消息体（用于日志输出）
	 * @param action       消费逻辑
	 */
	public static void safeConsume(Logger log, String topic, String consumerGroup,
			Object message, Runnable action) {
		String msgStr = message != null ? message.toString() : "null";
		try {
			log.info("[MQ消费] topic={}, group={}, message={}", topic, consumerGroup, msgStr);
			action.run();
			log.info("[MQ消费成功] topic={}, group={}", topic, consumerGroup);
		}
		catch (Exception e) {
			log.error("[MQ消费失败] topic={}, group={}, message={}, error={}",
					topic, consumerGroup, msgStr, e.getMessage(), e);
			throw e;
		}
	}

	/**
	 * 死信队列消息告警日志。
	 * <p>
	 * 在 DLQ 监控消费者中调用，用于记录进入死信队列的消息。
	 * </p>
	 *
	 * @param log           调用方的 Slf4j Logger
	 * @param originalTopic 原始消息主题
	 * @param consumerGroup 原始消费者组
	 * @param message       消息体
	 */
	public static void logDeadLetter(Logger log, String originalTopic,
			String consumerGroup, Object message) {
		log.error("[DLQ死信告警] 原始topic={}, 消费者组={}, 消息已进入死信队列，需人工介入! message={}",
				originalTopic, consumerGroup, message);
	}

}