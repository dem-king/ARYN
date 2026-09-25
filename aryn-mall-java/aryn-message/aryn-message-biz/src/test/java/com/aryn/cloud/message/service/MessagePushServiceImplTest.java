package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.entity.MessageParticipant;
import com.aryn.cloud.message.mapper.MessageAgentMapper;
import com.aryn.cloud.message.mapper.MessageParticipantMapper;
import com.aryn.cloud.message.service.impl.MessagePushServiceImpl;
import com.aryn.cloud.message.websocket.MessageWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MessagePushServiceImplTest {

	@Test
	void redisFailureDoesNotBlockLocalRealtimePush() {
		MessageParticipantMapper participantMapper = mock(MessageParticipantMapper.class);
		MessageAgentMapper agentMapper = mock(MessageAgentMapper.class);
		MessageWebSocketHandler handler = mock(MessageWebSocketHandler.class);
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		MessageParticipant participant = new MessageParticipant();
		participant.setParticipantType("MALL_USER");
		participant.setParticipantId("member-1");
		when(participantMapper.selectActiveParticipants("tenant-1", "conversation-1"))
			.thenReturn(List.of(participant));
		when(redisTemplate.convertAndSend(eq(MessagePushServiceImpl.CHANNEL), any()))
			.thenThrow(new IllegalStateException("redis unavailable"));
		MessagePushServiceImpl service = new MessagePushServiceImpl(participantMapper, agentMapper, handler,
				redisTemplate, new ObjectMapper());

		service.pushConversationMessage("tenant-1", "conversation-1", "message-1", 8L);

		verify(handler).send(argThat(event -> "message-1".equals(event.getMessageId())
				&& event.getSeqNo() == 8L && "member-1".equals(event.getRecipientId())));
	}

	@Test
	void queuedPoolEventReachesEveryEnabledAgent() {
		MessageParticipantMapper participantMapper = mock(MessageParticipantMapper.class);
		MessageAgentMapper agentMapper = mock(MessageAgentMapper.class);
		MessageWebSocketHandler handler = mock(MessageWebSocketHandler.class);
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		when(agentMapper.selectEnabledStaffIds("tenant-1", 500)).thenReturn(List.of("staff-1", "staff-2"));
		MessagePushServiceImpl service = new MessagePushServiceImpl(participantMapper, agentMapper, handler,
				redisTemplate, new ObjectMapper());

		service.pushConversationQueued("tenant-1", "conversation-1", "message-1", 3L);

		verify(handler, times(2)).send(argThat(event -> "CONVERSATION_QUEUED".equals(event.getEventType())
				&& "SYS_USER".equals(event.getRecipientType()) && "conversation-1".equals(event.getConversationId())));
	}

	@Test
	void queuedPoolEventSkipsDispatchWhenNoAgentEnabled() {
		MessageParticipantMapper participantMapper = mock(MessageParticipantMapper.class);
		MessageAgentMapper agentMapper = mock(MessageAgentMapper.class);
		MessageWebSocketHandler handler = mock(MessageWebSocketHandler.class);
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		when(agentMapper.selectEnabledStaffIds("tenant-1", 500)).thenReturn(List.of());
		MessagePushServiceImpl service = new MessagePushServiceImpl(participantMapper, agentMapper, handler,
				redisTemplate, new ObjectMapper());

		service.pushConversationQueued("tenant-1", "conversation-1", "message-1", 3L);

		verify(handler, never()).send(any());
	}

}
