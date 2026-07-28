package com.aryn.cloud.order.delivery.service;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTask;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DeliveryAssignmentNotifierTest {

	@Test
	void usesAttemptScopedEventAndBothEmployeeChannels() {
		RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
		DeliveryAssignmentNotifier notifier = notifier(rocketMQTemplate);

		notifier.notifyAssigned(task(), "staff-1", "配送员张三", 2);

		var captor = org.mockito.ArgumentCaptor.forClass(Message.class);
		verify(rocketMQTemplate).syncSend(eq(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC), captor.capture(),
			eq(RocketMqConstants.TIME_OUT));
		MessageSendCommand command = (MessageSendCommand) captor.getValue().getPayload();
		assertThat(command.getEventId()).isEqualTo("delivery-assigned:task-1:2");
		assertThat(command.getRecipientType()).isEqualTo("SYS_USER");
		assertThat(command.getChannels()).containsExactlyInAnyOrder("IN_APP", "WECHAT_SUBSCRIBE");
		assertThat(command.getMiniAppId()).isEqualTo("delivery-app");
	}

	@Test
	void mqFailureDoesNotEscapeAfterAssignmentCommit() {
		RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
		doThrow(new IllegalStateException("mq unavailable")).when(rocketMQTemplate)
			.syncSend(any(String.class), any(Message.class), eq(RocketMqConstants.TIME_OUT));

		assertThatCode(() -> notifier(rocketMQTemplate).notifyAssigned(task(), "staff-1", "配送员张三", 1))
			.doesNotThrowAnyException();
	}

	private DeliveryAssignmentNotifier notifier(RocketMQTemplate template) {
		DeliveryAssignmentNotifier notifier = new DeliveryAssignmentNotifier(template);
		ReflectionTestUtils.setField(notifier, "miniAppId", "delivery-app");
		ReflectionTestUtils.setField(notifier, "assignmentTemplateCode", "template-1");
		return notifier;
	}

	private OrderDeliveryTask task() {
		return new OrderDeliveryTask().setId("task-1")
			.setTenantId("tenant-1")
			.setOrderNo("ORDER-1");
	}
}
