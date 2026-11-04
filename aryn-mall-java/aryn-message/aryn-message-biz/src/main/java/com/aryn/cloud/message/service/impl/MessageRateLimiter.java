package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 客服消息发送频率限制（Redis 分钟桶）。
 *
 * <p>三维限流，任一超限即拒绝（业务错误「发送过于频繁，请稍后再试」）：</p>
 * <ol>
 *   <li>同一身份（tenant + 发送者类型 + 发送者 ID）默认 60 条/分钟；</li>
 *   <li>同一会话默认 30 条/分钟（防止单会话刷屏/机器人互喷）；</li>
 *   <li>同一租户默认 1200 条/分钟（给整体成本与下游推送兜底）。</li>
 * </ol>
 *
 * <p>实现为固定分钟桶：key 带 epoch 分钟，INCR 后首次计数设置 120s TTL（覆盖跨分钟边界）。
 * 三个阈值都带 {@code @Value} 默认值，boot/cloud 均无需新增配置文件；可用
 * {@code aryn.message.rate-limit.*} 覆盖。Redis 异常时降级放行并记日志：
 * 限流是纵深防御，不应让缓存故障阻断正常会话。</p>
 */
@Slf4j
@Component
public class MessageRateLimiter {

	private static final String KEY_PREFIX = "message:rate:send:";
	private static final long WINDOW_SECONDS = 120L;

	private final StringRedisTemplate redisTemplate;
	private final int identityPerMinute;
	private final int conversationPerMinute;
	private final int tenantPerMinute;

	public MessageRateLimiter(StringRedisTemplate redisTemplate,
			@Value("${aryn.message.rate-limit.identity-per-minute:60}") int identityPerMinute,
			@Value("${aryn.message.rate-limit.conversation-per-minute:30}") int conversationPerMinute,
			@Value("${aryn.message.rate-limit.tenant-per-minute:1200}") int tenantPerMinute) {
		this.redisTemplate = redisTemplate;
		this.identityPerMinute = identityPerMinute;
		this.conversationPerMinute = conversationPerMinute;
		this.tenantPerMinute = tenantPerMinute;
	}

	/**
	 * 校验本次发送是否在频控范围内，超限抛业务异常。
	 *
	 * <p>必须在幂等查询与写库之前调用：幂等重试也计入频控，避免同一条消息被无限重放。
	 */
	public void assertSendAllowed(String tenantId, String senderType, String senderId, String conversationId) {
		long epochMinute = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis());
		// 先查身份（最细粒度、最可能命中），再查会话、租户，超限时尽量少消耗后面的桶。
		assertWithinLimit("identity:" + tenantId + ":" + senderType + ":" + senderId + ":" + epochMinute,
				identityPerMinute);
		assertWithinLimit("conversation:" + tenantId + ":" + conversationId + ":" + epochMinute,
				conversationPerMinute);
		assertWithinLimit("tenant:" + tenantId + ":" + epochMinute, tenantPerMinute);
	}

	private void assertWithinLimit(String bucket, int limit) {
		String key = KEY_PREFIX + bucket;
		Long count;
		try {
			count = redisTemplate.opsForValue().increment(key);
			if (count != null && count == 1L) {
				redisTemplate.expire(key, Duration.ofSeconds(WINDOW_SECONDS));
			}
		}
		catch (RuntimeException exception) {
			log.warn("消息发送频控 Redis 不可用，降级放行 key={}", key, exception);
			return;
		}
		if (count != null && count > limit) {
			throw new ArynBusinessException("发送过于频繁，请稍后再试");
		}
	}

}
