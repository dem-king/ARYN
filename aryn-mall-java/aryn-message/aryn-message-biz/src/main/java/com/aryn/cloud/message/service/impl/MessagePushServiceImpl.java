package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.message.api.dto.push.MessagePushEvent;
import com.aryn.cloud.message.api.entity.MessageParticipant;
import com.aryn.cloud.message.mapper.MessageParticipantMapper;
import com.aryn.cloud.message.service.MessagePushService;
import com.aryn.cloud.message.websocket.MessageWebSocketHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** 本实例直推并通过 Redis Pub/Sub 广播到其他实例。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessagePushServiceImpl implements MessagePushService {

	public static final String CHANNEL = "message:push:events";

	private final MessageParticipantMapper participantMapper;
	private final MessageWebSocketHandler webSocketHandler;
	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final String instanceId = UUID.randomUUID().toString();

	@Override
	public void pushConversationMessage(String tenantId, String conversationId, String messageId, long seqNo) {
		for (MessageParticipant participant : participantMapper.selectActiveParticipants(tenantId, conversationId)) {
			MessagePushEvent event = new MessagePushEvent();
			event.setEventType("CONVERSATION_MESSAGE");
			event.setTenantId(tenantId);
			event.setRecipientType(participant.getParticipantType());
			event.setRecipientId(participant.getParticipantId());
			event.setConversationId(conversationId);
			event.setMessageId(messageId);
			event.setSeqNo(seqNo);
			event.setOriginInstanceId(instanceId);
			webSocketHandler.send(event);
			try {
				redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
			}
			catch (RuntimeException | JsonProcessingException exception) {
				log.warn("Redis 跨实例推送失败，保留本实例直推 tenantId={}, messageId={}", tenantId, messageId,
						exception);
			}
		}
	}

	@Override
	public void pushNotice(String tenantId, String recipientType, String recipientId, String messageId) {
		MessagePushEvent event = new MessagePushEvent();
		event.setEventType("NOTICE_RECEIVED");
		event.setTenantId(tenantId);
		event.setRecipientType(recipientType);
		event.setRecipientId(recipientId);
		event.setMessageId(messageId);
		event.setOriginInstanceId(instanceId);
		webSocketHandler.send(event);
		try {
			redisTemplate.convertAndSend(CHANNEL, objectMapper.writeValueAsString(event));
		}
		catch (RuntimeException | JsonProcessingException exception) {
			log.warn("Redis 通知跨实例推送失败，保留本实例直推 tenantId={}, messageId={}", tenantId, messageId,
					exception);
		}
	}

	public String getInstanceId() {
		return instanceId;
	}

}
