
package com.aryn.cloud.notify.websocket;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * 站内信 WebSocket 处理器
 * <p>
 * 握手时通过 URL 参数 token 进行 Sa-Token 校验，校验通过后将 userId 与 session 绑定。
 * 客户端连接示例：ws://host:port/ws/notify?token=xxx
 * </p>
 *
 * @author aryn
 * @since 2026/07/05
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotifyWebSocketHandler extends TextWebSocketHandler {

	private final WebSocketSessionManager sessionManager;

	@Override
	public void afterConnectionEstablished(WebSocketSession session) {
		String userId = extractUserId(session);
		if (userId == null) {
			log.warn("WebSocket 连接未携带有效 token，关闭连接");
			closeQuietly(session);
			return;
		}
		Map<String, Object> attrs = session.getAttributes();
		attrs.put("userId", userId);
		sessionManager.addSession(userId, session);
	}

	@Override
	protected void handleTextMessage(WebSocketSession session, TextMessage message) {
		// 心跳处理：客户端发送 "ping"，服务端回 "pong"
		String payload = message.getPayload();
		if ("ping".equalsIgnoreCase(payload)) {
			try {
				synchronized (session) {
					session.sendMessage(new TextMessage("pong"));
				}
			}
			catch (Exception e) {
				log.error("WebSocket 心跳回复失败", e);
			}
		}
	}

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		String userId = (String) session.getAttributes().get("userId");
		if (userId != null) {
			sessionManager.removeSession(userId);
		}
	}

	@Override
	public void handleTransportError(WebSocketSession session, Throwable exception) {
		log.error("WebSocket 传输错误: sessionId={}", session.getId(), exception);
		String userId = (String) session.getAttributes().get("userId");
		if (userId != null) {
			sessionManager.removeSession(userId);
		}
		closeQuietly(session);
	}

	/**
	 * 从握手请求中提取 userId：URL query 参数 token → Sa-Token 校验 → loginId
	 */
	private String extractUserId(WebSocketSession session) {
		String query = session.getUri() == null ? null : session.getUri().getQuery();
		if (StrUtil.isBlank(query)) {
			return null;
		}
		String token = extractParam(query, "token");
		if (StrUtil.isBlank(token)) {
			return null;
		}
		try {
			Object loginId = StpUtil.getLoginIdByToken(token);
			return loginId == null ? null : loginId.toString();
		}
		catch (Exception e) {
			log.warn("WebSocket token 校验失败: token={}", token);
			return null;
		}
	}

	private String extractParam(String query, String key) {
		for (String pair : query.split("&")) {
			String[] kv = pair.split("=", 2);
			if (kv.length == 2 && kv[0].equals(key)) {
				return kv[1];
			}
		}
		return null;
	}

	private void closeQuietly(WebSocketSession session) {
		try {
			if (session.isOpen()) {
				session.close();
			}
		}
		catch (Exception ignored) {
		}
	}

}
