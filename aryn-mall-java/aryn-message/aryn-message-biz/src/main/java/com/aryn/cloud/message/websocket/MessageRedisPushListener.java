package com.aryn.cloud.message.websocket;

import com.aryn.cloud.message.api.dto.push.MessagePushEvent;
import com.aryn.cloud.message.service.impl.MessagePushServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** 接收其他消息服务实例发布的轻量推送事件。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageRedisPushListener implements MessageListener {

	private final ObjectMapper objectMapper;
	private final MessageWebSocketHandler handler;
	private final MessagePushServiceImpl pushService;

	@Override
	public void onMessage(Message message, byte[] pattern) {
		try {
			MessagePushEvent event = objectMapper.readValue(new String(message.getBody(), StandardCharsets.UTF_8),
					MessagePushEvent.class);
			if (!pushService.getInstanceId().equals(event.getOriginInstanceId())) {
				handler.send(event);
			}
		}
		catch (Exception exception) {
			log.warn("处理 Redis 消息推送事件失败", exception);
		}
	}

}
