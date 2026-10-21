package com.aryn.cloud.upms.service.impl;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.sms.util.AliSmsUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 短信发送频控契约。
 *
 * <p>`GET /sms/{type}/{mobile}` 在免登白名单内，匿名可调；若没有频控，
 * 换手机号就能持续触发真实短信（短信轰炸/费用滥用）。这里按三个维度钉住：
 * 同号 60 秒冷却、同号 5 次/小时、同 IP 20 次/小时；同时守住
 * 「验证码仍在有效期内不重发、不消耗配额」与「register/forget 不发短信」。</p>
 */
class SysSmsServiceImplTest {

	private static final String MOBILE = "13800000000";

	private static final String CLIENT_IP = "10.1.2.3";

	private static final String CODE_KEY = CacheConstants.SMS_CODE_KEY + MOBILE;

	private static final String COOLDOWN_KEY = CacheConstants.SMS_SEND_COOLDOWN_KEY + MOBILE;

	private static final String PHONE_QUOTA_KEY = CacheConstants.SMS_SEND_PHONE_HOUR_KEY + MOBILE;

	private static final String IP_QUOTA_KEY = CacheConstants.SMS_SEND_IP_HOUR_KEY + CLIENT_IP;

	private static final String GLOBAL_QUOTA_KEY = CacheConstants.SMS_SEND_GLOBAL_HOUR_KEY;

	private StringRedisTemplate redisTemplate;

	private ValueOperations<String, String> valueOperations;

	private AliSmsUtils aliSmsUtils;

	private SysSmsServiceImpl service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		redisTemplate = mock(StringRedisTemplate.class);
		valueOperations = mock(ValueOperations.class);
		aliSmsUtils = mock(AliSmsUtils.class);
		service = new SysSmsServiceImpl(redisTemplate, aliSmsUtils);
	}

	@Test
	void existingCodeShortCircuitsWithoutSendingOrConsumingQuota() throws Exception {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(CODE_KEY)).thenReturn("1234");

		assertThat(service.sendSmsCode(MOBILE, "login", CLIENT_IP)).isTrue();

		verify(aliSmsUtils, never()).sendSms(anyString(), anyString());
		verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class));
		verify(valueOperations, never()).increment(anyString());
	}

	@Test
	void cooldownRejectsRepeatedSendWithinSixtySeconds() throws Exception {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(CODE_KEY)).thenReturn(null);
		when(valueOperations.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), eq(60L), eq(TimeUnit.SECONDS)))
			.thenReturn(false);

		assertThatThrownBy(() -> service.sendSmsCode(MOBILE, "login", CLIENT_IP))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg()).contains("过于频繁"));

		verify(aliSmsUtils, never()).sendSms(anyString(), anyString());
		verify(valueOperations, never()).increment(anyString());
	}

	@Test
	void phoneHourlyQuotaRejectsSixthSend() throws Exception {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(CODE_KEY)).thenReturn(null);
		when(valueOperations.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), eq(60L), eq(TimeUnit.SECONDS)))
			.thenReturn(true);
		when(valueOperations.increment(PHONE_QUOTA_KEY)).thenReturn(6L);

		assertThatThrownBy(() -> service.sendSmsCode(MOBILE, "login", CLIENT_IP))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg()).contains("发送次数已达上限"));

		verify(aliSmsUtils, never()).sendSms(anyString(), anyString());
		verify(valueOperations, never()).increment(IP_QUOTA_KEY);
	}

	@Test
	void ipHourlyQuotaRejectsTwentyFirstSend() throws Exception {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(CODE_KEY)).thenReturn(null);
		when(valueOperations.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), eq(60L), eq(TimeUnit.SECONDS)))
			.thenReturn(true);
		when(valueOperations.increment(PHONE_QUOTA_KEY)).thenReturn(1L);
		when(valueOperations.increment(IP_QUOTA_KEY)).thenReturn(21L);

		assertThatThrownBy(() -> service.sendSmsCode(MOBILE, "update", CLIENT_IP))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg()).contains("请求过于频繁"));

		verify(aliSmsUtils, never()).sendSms(anyString(), anyString());
	}

	@Test
	void globalHourlyQuotaRejectsSendEvenWhenIpIsForged() throws Exception {
		// IP 头可伪造（换 IP 绕过 IP 维度）：全局兜底上限必须仍然生效
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(CODE_KEY)).thenReturn(null);
		when(valueOperations.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), eq(60L), eq(TimeUnit.SECONDS)))
			.thenReturn(true);
		when(valueOperations.increment(PHONE_QUOTA_KEY)).thenReturn(1L);
		when(valueOperations.increment(IP_QUOTA_KEY)).thenReturn(1L);
		when(valueOperations.increment(GLOBAL_QUOTA_KEY)).thenReturn(301L);

		assertThatThrownBy(() -> service.sendSmsCode(MOBILE, "login", CLIENT_IP))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg()).contains("短信发送过于频繁"));

		verify(aliSmsUtils, never()).sendSms(anyString(), anyString());
	}

	@Test
	void sendsSmsAndStoresFourDigitCodeWithinQuota() throws Exception {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(CODE_KEY)).thenReturn(null);
		when(valueOperations.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), eq(60L), eq(TimeUnit.SECONDS)))
			.thenReturn(true);
		when(valueOperations.increment(PHONE_QUOTA_KEY)).thenReturn(1L);
		when(valueOperations.increment(IP_QUOTA_KEY)).thenReturn(1L);
		when(valueOperations.increment(GLOBAL_QUOTA_KEY)).thenReturn(1L);

		assertThat(service.sendSmsCode(MOBILE, "login", CLIENT_IP)).isTrue();

		ArgumentCaptor<String> sentTemplate = ArgumentCaptor.forClass(String.class);
		verify(aliSmsUtils).sendSms(eq(MOBILE), sentTemplate.capture());
		JSONObject templateParam = JSONUtil.parseObj(sentTemplate.getValue());
		String sentCode = templateParam.getStr("code");
		assertThat(sentCode).matches("\\d{4}");

		ArgumentCaptor<String> storedCode = ArgumentCaptor.forClass(String.class);
		verify(valueOperations).set(eq(CODE_KEY), storedCode.capture(), eq(300L), eq(TimeUnit.SECONDS));
		assertThat(storedCode.getValue()).isEqualTo(sentCode);
		// 配额 key 每次自增都续期，避免 key 永久残留
		verify(redisTemplate).expire(eq(PHONE_QUOTA_KEY), eq(3600L), eq(TimeUnit.SECONDS));
		verify(redisTemplate).expire(eq(IP_QUOTA_KEY), eq(3600L), eq(TimeUnit.SECONDS));
		verify(redisTemplate).expire(eq(GLOBAL_QUOTA_KEY), eq(3600L), eq(TimeUnit.SECONDS));
	}

	@Test
	void blankClientIpSkipsIpQuotaButKeepsPhoneQuota() throws Exception {
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		when(valueOperations.get(CODE_KEY)).thenReturn(null);
		when(valueOperations.setIfAbsent(eq(COOLDOWN_KEY), eq("1"), eq(60L), eq(TimeUnit.SECONDS)))
			.thenReturn(true);
		when(valueOperations.increment(PHONE_QUOTA_KEY)).thenReturn(1L);
		when(valueOperations.increment(GLOBAL_QUOTA_KEY)).thenReturn(1L);

		assertThat(service.sendSmsCode(MOBILE, "login", " ")).isTrue();

		verify(valueOperations, never()).increment(IP_QUOTA_KEY);
		verify(aliSmsUtils).sendSms(eq(MOBILE), argThat(template -> template.contains("code")));
	}

	@Test
	void registerAndForgetTypesNeitherSendNorConsumeQuota() {
		assertThat(service.sendSmsCode(MOBILE, "register", CLIENT_IP)).isTrue();
		assertThat(service.sendSmsCode(MOBILE, "forget", CLIENT_IP)).isTrue();

		verifyNoInteractions(aliSmsUtils);
		verifyNoInteractions(redisTemplate);
	}

}
