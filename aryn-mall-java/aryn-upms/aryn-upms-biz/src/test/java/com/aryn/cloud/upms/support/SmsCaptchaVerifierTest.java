package com.aryn.cloud.upms.support;

import com.aryn.cloud.common.core.constant.CacheConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 图形验证码二次校验契约：票据存在才放行，命中即删除（一次性）。
 */
class SmsCaptchaVerifierTest {

	private static final String VERIFICATION = "abc123";

	private static final String KEY = CacheConstants.CAPTCHA_SECOND_VERIFICATION_KEY + VERIFICATION;

	private StringRedisTemplate redisTemplate;

	private SmsCaptchaVerifier verifier;

	@BeforeEach
	void setUp() {
		redisTemplate = mock(StringRedisTemplate.class);
		verifier = new SmsCaptchaVerifier(redisTemplate);
	}

	@Test
	void missingOrBlankVerificationRejected() {
		assertThat(verifier.verifyAndConsume(null)).isFalse();
		assertThat(verifier.verifyAndConsume(" ")).isFalse();

		verifyNoInteractions(redisTemplate);
	}

	@Test
	void expiredVerificationRejected() {
		when(redisTemplate.hasKey(KEY)).thenReturn(false);

		assertThat(verifier.verifyAndConsume(VERIFICATION)).isFalse();

		verify(redisTemplate, never()).delete(anyString());
	}

	@Test
	void validVerificationPassesAndConsumesTicket() {
		when(redisTemplate.hasKey(KEY)).thenReturn(true);

		assertThat(verifier.verifyAndConsume(VERIFICATION)).isTrue();

		verify(redisTemplate).delete(KEY);
	}

	@Test
	void surroundingWhitespaceTrimmedBeforeLookup() {
		when(redisTemplate.hasKey(KEY)).thenReturn(true);

		assertThat(verifier.verifyAndConsume("  " + VERIFICATION + "  ")).isTrue();

		verify(redisTemplate).delete(KEY);
	}

}
