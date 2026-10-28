package com.aryn.cloud.auth.listener;

import com.aryn.cloud.common.core.constant.CacheConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 在线用户记录 key 生命周期：写入与注销/强退/顶号删除必须使用同一个 token 维度 key。
 *
 * <p>历史缺陷：写入是 {@code 前缀 + tenantId + ":" + token}，删除是 {@code 前缀 + token}，
 * key 永远不匹配，已下线会话的在线记录会残留到 30 天 TTL；单点登录后「顶号下线」是常态，
 * 该问题会直接表现为「在线用户」页大量假在线。
 */
class ArynSaTokenListenerTest {

	private StringRedisTemplate redisTemplate;

	private ArynSaTokenListener listener;

	@BeforeEach
	void setUp() {
		redisTemplate = mock(StringRedisTemplate.class);
		listener = new ArynSaTokenListener(mock(ApplicationEventPublisher.class), redisTemplate);
	}

	@Test
	void logoutRemovesTheSameKeyUsedAtLogin() {
		listener.doLogout("login", "user-1", "token-1");

		verify(redisTemplate).delete("satoken:login:online_user:token-1");
	}

	@Test
	void kickoutAndReplacedRemoveTheSameTokenScopedKey() {
		listener.doKickout("login", "user-1", "token-1");
		listener.doReplaced("login", "user-1", "token-2");

		verify(redisTemplate).delete("satoken:login:online_user:token-1");
		verify(redisTemplate).delete("satoken:login:online_user:token-2");
		verify(redisTemplate, times(2)).delete(org.mockito.ArgumentMatchers.anyString());
	}

	@Test
	void onlineUserKeyIsTokenScopedWithoutTenantSegment() {
		assertThat(ArynSaTokenListener.onlineUserKey("token-1"))
			.isEqualTo(CacheConstants.SYS_ONLINE_KEY + "token-1");
	}

	@Test
	void blankTokenDoesNotTouchRedis() {
		listener.doLogout("login", "user-1", " ");

		verifyNoInteractions(redisTemplate);
	}

}
