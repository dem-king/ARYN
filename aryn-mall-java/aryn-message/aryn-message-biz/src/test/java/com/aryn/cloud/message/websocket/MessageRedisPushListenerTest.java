package com.aryn.cloud.message.websocket;

import com.aryn.cloud.common.core.constant.SessionInvalidPushConstants;
import com.aryn.cloud.message.api.dto.push.MessagePushEvent;
import com.aryn.cloud.message.service.impl.MessagePushServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Redis 会话失效推送事件的跨实例投递（三期）。
 *
 * <p>发布方（common-security）写的是纯 JSON；监听方用 {@link MessagePushEvent} 反序列化后交给
 * WebSocket handler 按 {@code tenantId:recipientType:recipientId} 路由。这里验证：
 * ① 外部实例发布的事件能完整带到 handler（含 reason/subReason/device）；
 * ② 本实例直接发布的事件不重复投递（originInstanceId 相同则跳过）；
 * ③ 非法 JSON 不抛异常、不影响其它推送。</p>
 */
class MessageRedisPushListenerTest {

	private static final String CHANNEL = SessionInvalidPushConstants.CHANNEL;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private final MessageWebSocketHandler handler = mock(MessageWebSocketHandler.class);

	private final MessagePushServiceImpl pushService = mock(MessagePushServiceImpl.class);

	@Test
	void routesForeignSessionInvalidEventToWebSocketHandler() {
		when(pushService.getInstanceId()).thenReturn("instance-1");
		String json = """
				{"eventType":"SESSION_INVALID","tenantId":"t1","recipientType":"SYS_USER","recipientId":"42",
				 "reason":"KICK_OUT","subReason":"ACCOUNT_DISABLED","invalidAt":"2026-10-29T09:12:33+08:00",
				 "device":{"os":"Windows 10","browser":"Chrome 120","location":"上海市"},
				 "originInstanceId":"publisher-uuid"}""";
		MessageRedisPushListener listener = new MessageRedisPushListener(objectMapper, handler, pushService);

		listener.onMessage(message(json), null);

		ArgumentCaptor<MessagePushEvent> captor = ArgumentCaptor.forClass(MessagePushEvent.class);
		verify(handler).send(captor.capture());
		MessagePushEvent event = captor.getValue();
		assertThat(event.getEventType()).isEqualTo(SessionInvalidPushConstants.EVENT_TYPE_SESSION_INVALID);
		assertThat(event.getTenantId()).isEqualTo("t1");
		assertThat(event.getRecipientType()).isEqualTo(SessionInvalidPushConstants.RECIPIENT_TYPE_SYS_USER);
		assertThat(event.getRecipientId()).isEqualTo("42");
		assertThat(event.getReason()).isEqualTo("KICK_OUT");
		assertThat(event.getSubReason()).isEqualTo("ACCOUNT_DISABLED");
		assertThat(event.getInvalidAt()).isNotBlank();
		assertThat(event.getDevice()).containsEntry("os", "Windows 10").containsEntry("location", "上海市");
	}

	@Test
	void skipsEventsPublishedByCurrentInstance() {
		when(pushService.getInstanceId()).thenReturn("instance-1");
		String json = """
				{"eventType":"SESSION_INVALID","tenantId":"t1","recipientType":"SYS_USER","recipientId":"42",
				 "reason":"BE_REPLACED","originInstanceId":"instance-1"}""";
		MessageRedisPushListener listener = new MessageRedisPushListener(objectMapper, handler, pushService);

		listener.onMessage(message(json), null);

		verifyNoInteractions(handler);
	}

	@Test
	void malformedPayloadIsSwallowed() {
		when(pushService.getInstanceId()).thenReturn("instance-1");
		MessageRedisPushListener listener = new MessageRedisPushListener(objectMapper, handler, pushService);

		assertThatCode(() -> listener.onMessage(message("not-a-json"), null)).doesNotThrowAnyException();
		verifyNoInteractions(handler);
	}

	private static Message message(String body) {
		return new DefaultMessage(CHANNEL.getBytes(StandardCharsets.UTF_8), body.getBytes(StandardCharsets.UTF_8));
	}

}
