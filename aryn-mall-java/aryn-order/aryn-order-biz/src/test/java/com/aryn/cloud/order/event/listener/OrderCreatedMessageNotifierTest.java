package com.aryn.cloud.order.event.listener;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.event.ArynOrderCreateAfterEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderCreatedMessageNotifierTest {

	@Test
	void afterCommitListenerPublishesTenantBoundMessageCommand() {
		RocketMQTemplate template = mock(RocketMQTemplate.class);
		OrderCreatedMessageNotifier notifier = new OrderCreatedMessageNotifier(template, new ObjectMapper());
		OrderInfo order = new OrderInfo();
		order.setId("order-1");
		order.setOrderNo("NO-1");
		order.setTenantId("tenant-1");
		order.setUserId("member-1");
		order.setPaymentPrice(new BigDecimal("88.00"));

		notifier.notifyMember(new ArynOrderCreateAfterEvent(this, order, List.of(), "2"));

		ArgumentCaptor<Message<MessageSendCommand>> captor = ArgumentCaptor.forClass(Message.class);
		verify(template).syncSend(eq(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC), captor.capture(),
				eq(RocketMqConstants.TIME_OUT));
		MessageSendCommand command = captor.getValue().getPayload();
		assertThat(command.getEventId()).isEqualTo("order-created:order-1");
		assertThat(command.getTenantId()).isEqualTo("tenant-1");
		assertThat(command.getRecipientId()).isEqualTo("member-1");
		assertThat(command.getCardPayload()).contains("\"orderId\":\"order-1\"");
	}

}
