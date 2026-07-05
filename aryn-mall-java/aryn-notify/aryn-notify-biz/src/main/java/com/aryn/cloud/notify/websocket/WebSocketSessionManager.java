
package com.aryn.cloud.notify.websocket;

import com.aryn.cloud.notify.api.vo.NotifyMessageVO;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话管理器（单实例版）
 * <p>
 * 单实例部署下使用本地 ConcurrentHashMap 管理在线会话即可。
 * 若未来需要多实例部署，需引入 Redis Pub/Sub 跨节点广播。
 * </p>
 *
 * @author aryn
 * @since 2026/07/05
 */
@Slf4j
@Component
public class WebSocketSessionManager {

	/** 本机在线会话：userId → WebSocketSession */
	private final ConcurrentHashMap<String, org.springframework.web.socket.WebSocketSession> localSessions = new ConcurrentHashMap<>();

	/**
	 * 添加会话
	 * @param userId 用户ID
	 * @param session WebSocket 会话
	 */
	public void addSession(String userId, org.springframework.web.socket.WebSocketSession session) {
		localSessions.put(userId, session);
		log.info("用户 {} WebSocket 已连接，当前在线人数: {}", userId, localSessions.size());
	}

	/**
	 * 移除会话
	 * @param userId 用户ID
	 */
	public void removeSession(String userId) {
		localSessions.remove(userId);
		log.info("用户 {} WebSocket 已断开，当前在线人数: {}", userId, localSessions.size());
	}

	/**
	 * 用户是否在线
	 * @param userId 用户ID
	 * @return 是否在线
	 */
	public boolean isOnline(String userId) {
		return localSessions.containsKey(userId);
	}

	/**
	 * 向指定用户推送消息
	 * @param userId 用户ID
	 * @param message 消息内容
	 */
	public void sendToUser(String userId, NotifyMessageVO message) {
		org.springframework.web.socket.WebSocketSession session = localSessions.get(userId);
		if (session == null || !session.isOpen()) {
			return;
		}
		try {
			org.springframework.web.socket.TextMessage textMessage = new org.springframework.web.socket.TextMessage(
					JSONUtil.toJsonStr(message));
			synchronized (session) {
				session.sendMessage(textMessage);
			}
		}
		catch (Exception e) {
			log.error("WebSocket 推送失败: userId={}", userId, e);
		}
	}

}
