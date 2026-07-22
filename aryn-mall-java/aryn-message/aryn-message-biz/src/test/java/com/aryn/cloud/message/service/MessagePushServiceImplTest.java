package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.entity.MessageParticipant;
import com.aryn.cloud.message.mapper.MessageParticipantMapper;
import com.aryn.cloud.message.service.impl.MessagePushServiceImpl;
import com.aryn.cloud.message.websocket.MessageWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessagePushServiceImplTest {

	@Test
	void redisFailureDoesNotBlockLocalRealtimePush() {
		MessageParticipantMapper participantMapper = mock(MessageParticipantMapper.class);
		MessageWebSocketHandler handler = mock(MessageWebSocketHandler.class);
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		MessageParticipant participant = new MessageParticipant();
		participant.setParticipantType("MALL_USER");
		participant.setParticipantId("member-1");
		when(participantMapper.selectActiveParticipants("tenant-1", "conversation-1"))
			.thenReturn(List.of(participant));
		when(redisTemplate.convertAndSend(eq(MessagePushServiceImpl.CHANNEL), any()))
			.thenThrow(new IllegalStateException("redis unavailable"));
		MessagePushServiceImpl service = new MessagePushServiceImpl(participantMapper, handler, redisTemplate,
				new ObjectMapper());

		service.pushConversationMessage("tenant-1", "conversation-1", "message-1", 8L);

		verify(handler).send(org.mockito.ArgumentMatchers.argThat(event -> "message-1".equals(event.getMessageId())
				&& event.getSeqNo() == 8L && "member-1".equals(event.getRecipientId())));
	}

}
