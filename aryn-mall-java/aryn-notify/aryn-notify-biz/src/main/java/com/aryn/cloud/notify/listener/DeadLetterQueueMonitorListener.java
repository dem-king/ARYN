
package com.aryn.cloud.notify.listener;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.util.RocketMqConsumerHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 死信队列监控消费者
 * <p>
 * 监控各消费者组的死信队列（%DLQ%consumerGroup），当消息重试达到最大次数后进入死信队列，
 * 本消费者负责记录告警日志，便于运维人工介入处理。
 * </p>
 *
 * @author aryn
 * @since 2026/07/05
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = RocketMqConstants.DLQ_MONITOR_TOPIC,
		consumerGroup = "dlq-monitor-consumer-group",
		maxReconsumeTimes = 1)
public class DeadLetterQueueMonitorListener implements RocketMQListener<String> {

	@Override
	public void onMessage(String message) {
		RocketMqConsumerHelper.logDeadLetter(log, RocketMqConstants.DLQ_MONITOR_TOPIC,
				"dlq-monitor-consumer-group", message);
		// 后续可扩展：发送告警邮件/钉钉通知、写入告警表等
	}

}