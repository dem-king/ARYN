package com.aryn.cloud.message.websocket;

import com.aryn.cloud.message.service.impl.MessagePushServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** 消息跨实例 Redis Pub/Sub 配置。 */
@Configuration
@RequiredArgsConstructor
public class MessageRedisPushConfig {

	private final MessageRedisPushListener listener;

	@Bean
	public RedisMessageListenerContainer messageRedisListenerContainer(RedisConnectionFactory connectionFactory) {
		RedisMessageListenerContainer container = new RedisMessageListenerContainer();
		container.setConnectionFactory(connectionFactory);
		container.addMessageListener(listener, new ChannelTopic(MessagePushServiceImpl.CHANNEL));
		return container;
	}

}
