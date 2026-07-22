package com.aryn.cloud.order.event.listener;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.event.ArynOrderCreateAfterEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

/** 订单提交后发送不影响核心交易的站内通知命令。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreatedMessageNotifier {

	private final RocketMQTemplate rocketMQTemplate;
	private final ObjectMapper objectMapper;

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void notifyMember(ArynOrderCreateAfterEvent event) {
		OrderInfo order = event.getOrderInfo();
		if (order == null) {
			return;
		}
		try {
			MessageSendCommand command = new MessageSendCommand();
			command.setEventId("order-created:" + order.getId());
			command.setTenantId(order.getTenantId());
			command.setRecipientType(MessageIdentityType.MALL_USER.name());
			command.setRecipientId(order.getUserId());
			command.setCategory("ORDER");
			command.setTitle("订单已提交");
			command.setSummary("订单 " + order.getOrderNo() + " 已创建");
			command.setContent("您的订单已提交，请在有效时间内完成支付。");
			command.setBizType("ORDER_CREATE");
			command.setBizId(order.getId());
			command.setCardPayload(json(Map.of("orderId", order.getId(), "title", "订单 " + order.getOrderNo(),
					"amount", String.valueOf(order.getPaymentPrice()))));
			command.setJumpPayload(json(Map.of("bizType", "ORDER", "bizId", order.getId())));
			rocketMQTemplate.syncSend(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC, new GenericMessage<>(command),
					RocketMqConstants.TIME_OUT);
		}
		catch (RuntimeException exception) {
			log.error("订单创建站内通知发送失败，不回滚订单, orderId={}", order.getId(), exception);
		}
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("订单通知序列化失败", exception);
		}
	}

}
