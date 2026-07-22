package com.aryn.cloud.message.websocket;

import com.aryn.cloud.message.api.dto.push.MessagePushEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 只下发轻量事件的 WebSocket handler；消息事实仍由 HTTP/MySQL 提供。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageWebSocketHandler extends TextWebSocketHandler {

	private final ObjectMapper objectMapper;
	private final ConcurrentHashMap<String, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		sessions.computeIfAbsent(identityKey(session), ignored -> ConcurrentHashMap.newKeySet()).add(session);
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
		if ("PING".equalsIgnoreCase(message.getPayload())) {
			session.sendMessage(new TextMessage("PONG"));
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		remove(session);
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
		remove(session);
		if (session.isOpen()) {
			session.close(CloseStatus.SERVER_ERROR);
		}
	}

	public void send(MessagePushEvent event) {
		Set<WebSocketSession> targets = sessions.get(identityKey(event.getTenantId(), event.getRecipientType(),
				event.getRecipientId()));
		if (targets == null || targets.isEmpty()) {
			return;
		}
		String payload;
		try {
			payload = objectMapper.writeValueAsString(event);
		}
		catch (JsonProcessingException exception) {
			log.error("序列化消息推送事件失败 eventType={}, messageId={}", event.getEventType(), event.getMessageId(),
					exception);
			return;
		}
		for (WebSocketSession session : targets) {
			try {
				if (session.isOpen()) {
					synchronized (session) {
						session.sendMessage(new TextMessage(payload));
					}
				}
			}
			catch (IOException exception) {
				log.warn("WebSocket 事件推送失败 sessionId={}, messageId={}", session.getId(), event.getMessageId(),
						exception);
				remove(session);
			}
		}
	}

	private void remove(WebSocketSession session) {
		Set<WebSocketSession> identitySessions = sessions.get(identityKey(session));
		if (identitySessions != null) {
			identitySessions.remove(session);
			if (identitySessions.isEmpty()) {
				sessions.remove(identityKey(session), identitySessions);
			}
		}
	}

	private String identityKey(WebSocketSession session) {
		return identityKey((String) session.getAttributes().get(MessageHandshakeInterceptor.ATTR_TENANT_ID),
				(String) session.getAttributes().get(MessageHandshakeInterceptor.ATTR_IDENTITY_TYPE),
				(String) session.getAttributes().get(MessageHandshakeInterceptor.ATTR_IDENTITY_ID));
	}

	private String identityKey(String tenantId, String identityType, String identityId) {
		return tenantId + ":" + identityType + ":" + identityId;
	}

}
