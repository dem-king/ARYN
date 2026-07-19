package com.aryn.cloud.order.listener;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.order.api.dto.OrderConsumerDTO;
import com.aryn.cloud.order.api.entity.OrderConfig;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.event.ArynOrderCreateAfterEvent;
import com.aryn.cloud.order.event.listener.ArynOrderCreateAfterEventListener;
import com.aryn.cloud.order.service.IOrderConfigService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArynOrderCreateAfterEventListenerTest {

	@Test
	void invalidDelayLevelUsesDefaultAndOrderTenantSnapshot() {
		RocketMQTemplate template = mock(RocketMQTemplate.class);
		IOrderConfigService configService = mock(IOrderConfigService.class);
		OrderConfig config = new OrderConfig();
		config.setOrderCancelTimeout("invalid");
		when(configService.getConfig()).thenReturn(config);
		ArynOrderCreateAfterEventListener listener =
			new ArynOrderCreateAfterEventListener(template, configService);

		listener.sendMqEventListener(event());

		ArgumentCaptor<Message<OrderConsumerDTO>> captor = ArgumentCaptor.forClass(Message.class);
		verify(template).syncSend(eq(RocketMqConstants.ORDER_CANCEL_TOPIC), captor.capture(),
			eq(RocketMqConstants.TIME_OUT), eq(RocketMqConstants.ORDER_CANCEL_LEVEL));
		assertThat(captor.getValue().getPayload().getOrderId()).isEqualTo("order-1");
		assertThat(captor.getValue().getPayload().getTenantId()).isEqualTo("tenant-1");
	}

	@Test
	void sendFailureDoesNotTurnCommittedOrderIntoApiFailure() {
		RocketMQTemplate template = mock(RocketMQTemplate.class);
		IOrderConfigService configService = mock(IOrderConfigService.class);
		when(configService.getConfig()).thenReturn(new OrderConfig());
		doThrow(new IllegalStateException("mq unavailable")).when(template)
			.syncSend(eq(RocketMqConstants.ORDER_CANCEL_TOPIC), any(Message.class),
				eq(RocketMqConstants.TIME_OUT), eq(RocketMqConstants.ORDER_CANCEL_LEVEL));
		ArynOrderCreateAfterEventListener listener =
			new ArynOrderCreateAfterEventListener(template, configService);

		assertThatCode(() -> listener.sendMqEventListener(event())).doesNotThrowAnyException();
	}

	private ArynOrderCreateAfterEvent event() {
		OrderInfo order = new OrderInfo();
		order.setId("order-1");
		order.setTenantId("tenant-1");
		return new ArynOrderCreateAfterEvent(this, order, List.of(), "2");
	}
}
