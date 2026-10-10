
package com.aryn.cloud.order.event.listener;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.order.api.dto.OrderConsumerDTO;
import com.aryn.cloud.order.api.entity.OrderConfig;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.event.ArynOrderCreateAfterEvent;
import com.aryn.cloud.order.service.IOrderConfigService;
import com.aryn.cloud.order.support.OrderCancelTimeoutHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Objects;

/**
 * 创建订单后事件监听
 *
 * @author: aryn
 * @date: 2023/4/24 11:57
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ArynOrderCreateAfterEventListener {

	private final RocketMQTemplate rocketMQTemplate;

	private final IOrderConfigService orderConfigService;

	/**
	 * rocketmq 延迟消息：按订单配置的延迟级别超时取消订单
	 * @param event
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void sendMqEventListener(ArynOrderCreateAfterEvent event) {
		OrderConfig orderConfig = orderConfigService.getConfig();
		if (Objects.isNull(orderConfig)) {
			return;
		}

		final OrderInfo orderInfo = event.getOrderInfo();
		int level = OrderCancelTimeoutHelper.resolveDelayLevel(orderConfig);
		OrderConsumerDTO orderConsumerDTO = new OrderConsumerDTO();
		orderConsumerDTO.setOrderId(orderInfo.getId());
		orderConsumerDTO.setTenantId(orderInfo.getTenantId());
		try {
			rocketMQTemplate.syncSend(RocketMqConstants.ORDER_CANCEL_TOPIC, new GenericMessage<>(orderConsumerDTO),
					RocketMqConstants.TIME_OUT, level);
		}
		catch (RuntimeException exception) {
			log.error("订单延迟取消消息发送失败，将由超时扫描兜底, orderId={}", orderInfo.getId(), exception);
		}
	}

}
