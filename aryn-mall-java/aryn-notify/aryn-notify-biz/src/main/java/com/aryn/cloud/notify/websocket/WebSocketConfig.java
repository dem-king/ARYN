
package com.aryn.cloud.notify.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket 配置
 * <p>
 * 单实例部署下直接注册 Handler。
 * 注意：路径 /ws/notify 需在 Sa-Token 白名单中放行（已在 application.yml 中配置）。
 * </p>
 *
 * @author aryn
 * @since 2026/07/05
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

	private final NotifyWebSocketHandler notifyWebSocketHandler;

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(notifyWebSocketHandler, "/ws/notify").setAllowedOrigins("*");
	}

}
