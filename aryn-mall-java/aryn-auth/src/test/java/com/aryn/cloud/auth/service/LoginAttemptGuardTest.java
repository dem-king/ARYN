package com.aryn.cloud.auth.service;

import com.aryn.cloud.auth.config.LoginProtectionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 登录失败限流契约：账号/IP 双维度计数、锁定剩余时间、成功只清账号维度。
 *
 * <p>这里通过 mock Redis 脚本返回值钉住「放行 / 锁定」判定与 key 口径，
 * 不依赖真实 Redis 服务。</p>
 */
class LoginAttemptGuardTest {

	private StringRedisTemplate redisTemplate;

	private LoginProtectionProperties properties;

	private LoginAttemptGuard guard;

	@BeforeEach
	void setUp() {
		redisTemplate = mock(StringRedisTemplate.class);
		properties = new LoginProtectionProperties();
		guard = new LoginAttemptGuard(redisTemplate, properties);
	}

	/** 脚本调用固定为 5 个 ARGV 参数（账号上限、IP 上限、账号锁定、IP 开关、IP 锁定） */
	private void givenScriptReturns(Long value) {
		when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any()))
			.thenReturn(value);
	}

	@Test
	void returnsZeroWhenNotLocked() {
		givenScriptReturns(0L);

		assertThat(guard.retryAfterSeconds("Admin", "10.0.0.1")).isZero();
	}

	@Test
	void returnsRemainingLockSeconds() {
		givenScriptReturns(420L);

		assertThat(guard.retryAfterSeconds("admin", "10.0.0.1")).isEqualTo(420L);
	}

	@Test
	void accountKeyIsLowerCasedAndPrefixed() {
		givenScriptReturns(0L);

		guard.retryAfterSeconds("  AdminUser  ", "10.0.0.1");

		ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
		verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), any(), any(), any(), any(), any());
		assertThat(keys.getValue()).containsExactly(
				"login:fail:account:adminuser",
				"login:fail:ip:10.0.0.1");
	}

	@Test
	void blankIpSkipsIpDimensionButKeepsAccountDimension() {
		givenScriptReturns(0L);

		guard.retryAfterSeconds("admin", null);

		ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
		verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), any(), any(), any(), any(), any());
		assertThat(keys.getValue()).containsExactly(
				"login:fail:account:admin",
				"login:fail:account:admin");
	}

	@Test
	void recordFailureWritesBothDimensionKeys() {
		givenScriptReturns(1L);

		guard.recordFailure("admin", "10.0.0.1");

		ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
		verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), any(), any(), any());
		assertThat(keys.getValue()).containsExactly(
				"login:fail:account:admin",
				"login:fail:ip:10.0.0.1");
	}

	@Test
	void resetOnlyDeletesAccountKey() {
		guard.resetAccount("Admin");

		verify(redisTemplate).delete("login:fail:account:admin");
	}

	@Test
	void disabledProtectionSkipsRedisCompletely() {
		properties.setEnabled(false);

		assertThat(guard.retryAfterSeconds("admin", "10.0.0.1")).isZero();
		guard.recordFailure("admin", "10.0.0.1");
		verifyNoInteractions(redisTemplate);
	}

	@Test
	void redisFailureOnCheckFailsClosedWithLockWindow() {
		when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any(), any(), any()))
			.thenThrow(new IllegalStateException("redis down"));

		assertThat(guard.retryAfterSeconds("admin", "10.0.0.1"))
			.isEqualTo(properties.getAccountLockSeconds());
	}

	@Test
	void redisFailureOnRecordOnlyLogsAndDoesNotThrow() {
		when(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any()))
			.thenThrow(new IllegalStateException("redis down"));

		guard.recordFailure("admin", "10.0.0.1");

		// 写失败只告警，不影响调用方流程（密码校验本身已失败）
		verify(redisTemplate, never()).delete(anyString());
	}

}
