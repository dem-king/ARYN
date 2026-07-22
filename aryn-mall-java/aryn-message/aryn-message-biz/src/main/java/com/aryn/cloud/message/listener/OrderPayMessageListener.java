package com.aryn.cloud.message.listener;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.OrderPaySuccessEvent;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.service.MessageCommandService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 将订单支付成功事实转换为会员站内通知。 */
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = RocketMqConstants.ORDER_PAY_SUCCESS_NOTIFY_TOPIC,
		consumerGroup = "message-order-pay-consumer")
public class OrderPayMessageListener implements RocketMQListener<OrderPaySuccessEvent> {

	private final MessageCommandService commandService;
	private final ObjectMapper objectMapper;

	@Override
	public void onMessage(OrderPaySuccessEvent event) {
		ArynTenantContextHolder.removeTenantId();
		try {
			ArynTenantContextHolder.setTenantId(event.getTenantId());
			MessageSendCommand command = new MessageSendCommand();
			command.setEventId("order-paid:" + event.getOrderId());
			command.setTenantId(event.getTenantId());
			command.setRecipientType(MessageIdentityType.MALL_USER.name());
			command.setRecipientId(event.getUserId());
			command.setCategory("ORDER");
			command.setTitle("订单支付成功");
			command.setSummary("订单 " + event.getOrderNo() + " 已支付成功");
			command.setContent("您的订单已支付成功，商家将尽快为您处理。");
			command.setBizType("ORDER_PAY");
			command.setBizId(event.getOrderId());
			command.setCardPayload(json(Map.of("orderId", event.getOrderId(), "title", "订单 " + event.getOrderNo(),
					"amount", String.valueOf(event.getPaymentPrice()))));
			command.setJumpPayload(json(Map.of("bizType", "ORDER", "bizId", event.getOrderId())));
			commandService.consume(command);
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("订单支付通知序列化失败", exception);
		}
	}

}
