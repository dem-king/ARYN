package com.aryn.cloud.message.websocket;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** C 端与工作人员分离的 WebSocket 握手入口。 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class MessageWebSocketConfig implements WebSocketConfigurer {

	private final MessageWebSocketHandler handler;

	@Override
	public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
		registry.addHandler(handler, "/ws/app")
			.addInterceptors(new MessageHandshakeInterceptor(DeviceTypeEnum.TOC))
			.setAllowedOriginPatterns("*");
		registry.addHandler(handler, "/ws/staff")
			.addInterceptors(new MessageHandshakeInterceptor(DeviceTypeEnum.TOB))
			.setAllowedOriginPatterns("*");
	}

}
