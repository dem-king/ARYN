package com.aryn.cloud.common.security.service;

import com.aryn.cloud.common.core.constant.CacheConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 短信验证码校验器契约：原子「校验 + 一次性消费 + 失败计数」。
 *
 * <p>4 位验证码若没有失败次数上限，5 分钟有效期内可被枚举。这里通过 Lua 返回值
 * 映射钉住三种结果，并断言 key/参数口径（验证码 key 与失败计数 key 分开）。</p>
 */
class SmsCodeVerifierTest {

	private static final String PHONE = "13800000000";

	private static final String CODE = "1234";

	private static final String CODE_KEY = CacheConstants.SMS_CODE_KEY + PHONE;

	private static final String ATTEMPT_KEY = CacheConstants.SMS_CODE_ATTEMPT_KEY + PHONE;

	private StringRedisTemplate redisTemplate;

	private SmsCodeVerifier verifier;

	@BeforeEach
	void setUp() {
		redisTemplate = mock(StringRedisTemplate.class);
		verifier = new SmsCodeVerifier(redisTemplate);
	}

	@Test
	void successResultConsumesCodeAndAttemptsKeys() {
		when(redisTemplate.execute(any(RedisScript.class), anyList(), eq(CODE), eq("5"))).thenReturn(1L);

		assertThat(verifier.verifyAndConsume(PHONE, CODE)).isEqualTo(SmsCodeVerifier.Result.SUCCESS);

		ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
		verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), eq(CODE), eq("5"));
		assertThat(keys.getValue()).containsExactly(CODE_KEY, ATTEMPT_KEY);
	}

	@Test
	void mismatchReturnsInvalid() {
		when(redisTemplate.execute(any(RedisScript.class), anyList(), eq(CODE), eq("5"))).thenReturn(0L);

		assertThat(verifier.verifyAndConsume(PHONE, CODE)).isEqualTo(SmsCodeVerifier.Result.INVALID);
	}

	@Test
	void attemptsExceededMarksCodeAsInvalidated() {
		when(redisTemplate.execute(any(RedisScript.class), anyList(), eq(CODE), eq("5"))).thenReturn(-1L);

		assertThat(verifier.verifyAndConsume(PHONE, CODE)).isEqualTo(SmsCodeVerifier.Result.TOO_MANY_ATTEMPTS);
	}

	@Test
	void nullResultFromRedisTreatedAsInvalid() {
		when(redisTemplate.execute(any(RedisScript.class), anyList(), eq(CODE), eq("5"))).thenReturn(null);

		assertThat(verifier.verifyAndConsume(PHONE, CODE)).isEqualTo(SmsCodeVerifier.Result.INVALID);
	}

	@Test
	void blankInputsRejectedWithoutTouchingRedis() {
		assertThat(verifier.verifyAndConsume(null, CODE)).isEqualTo(SmsCodeVerifier.Result.INVALID);
		assertThat(verifier.verifyAndConsume("", CODE)).isEqualTo(SmsCodeVerifier.Result.INVALID);
		assertThat(verifier.verifyAndConsume(PHONE, null)).isEqualTo(SmsCodeVerifier.Result.INVALID);
		assertThat(verifier.verifyAndConsume(PHONE, " ")).isEqualTo(SmsCodeVerifier.Result.INVALID);

		verifyNoInteractions(redisTemplate);
	}

}
