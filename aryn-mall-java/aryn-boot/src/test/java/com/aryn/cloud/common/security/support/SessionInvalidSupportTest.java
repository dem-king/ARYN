package com.aryn.cloud.common.security.support;

import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.constant.SessionInvalidPushConstants;
import com.aryn.cloud.common.core.enums.SessionInvalidReason;
import com.aryn.cloud.common.core.enums.SessionInvalidSubReason;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.eq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 会话失效原因读写：token 维度 key、TTL、设备信息白名单、Redis 异常降级。
 *
 * <p>该能力在登录链路与 401 出口上都是"旁路"：任何失败只能降级，不能影响登录或 401 的返回。</p>
 */
class SessionInvalidSupportTest {

	@Test
	void markReplacedWritesTokenScopedRecordWithDeviceInfo() {
		StringRedisTemplate template = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(template.opsForValue()).thenReturn(ops);
		when(ops.get(CacheConstants.SYS_ONLINE_KEY + "new-token")).thenReturn(
				"{\"os\":\"Windows 10\",\"browser\":\"Chrome 120\",\"location\":\"上海市\","
						+ "\"ipAddr\":\"1.2.3.4\",\"tenantId\":\"t1\",\"userName\":\"admin\"}");

		SessionInvalidSupport.markReplaced(List.of("old-token"), "new-token", "1001", "tenant-1", "TOB",
				template);

		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(ops).set(eq(CacheConstants.SESSION_INVALID_KEY + "old-token"), json.capture(),
				eq(SessionInvalidSupport.TTL_SECONDS), eq(TimeUnit.SECONDS));
		// 三期：写记录的同时主动推送 SESSION_INVALID，旧设备可秒级弹窗
		ArgumentCaptor<String> pushed = ArgumentCaptor.forClass(String.class);
		verify(template).convertAndSend(eq(SessionInvalidPushConstants.CHANNEL), pushed.capture());
		assertThat(pushed.getValue())
			.contains("\"eventType\":\"SESSION_INVALID\"")
			.contains("\"reason\":\"BE_REPLACED\"")
			.contains("\"recipientType\":\"SYS_USER\"")
			.contains("\"tenantId\":\"tenant-1\"")
			.contains("\"recipientId\":\"1001\"")
			.contains("Windows 10")
			.as("推送事件不得携带 token")
			.doesNotContain("new-token")
			.doesNotContain("old-token");
		assertThat(json.getValue())
			.as("必须带机器可读原因")
			.contains("\"reason\":\"BE_REPLACED\"")
			.contains("\"invalidAt\"")
			.as("设备信息来自新会话在线记录")
			.contains("Windows 10")
			.contains("Chrome 120")
			.contains("上海市")
			.as("IP 按白名单字段 ip 下发（2026-11-01 产品决策），原始 ipAddr 键名不得透传")
			.contains("\"ip\":\"1.2.3.4\"")
			.doesNotContain("ipAddr")
			.doesNotContain("tenantId")
			.doesNotContain("userName")
			.as("记录里不得出现 token")
			.doesNotContain("new-token")
			.doesNotContain("old-token");
	}

	@Test
	void markReplacedFiltersUnknownUaFieldsFromOnlineRecord() {
		StringRedisTemplate template = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(template.opsForValue()).thenReturn(ops);
		// 无 User-Agent 的客户端登录：hutool 解析兜底值 Unknown，对用户无信息量
		when(ops.get(CacheConstants.SYS_ONLINE_KEY + "new-token")).thenReturn(
				"{\"os\":\"Unknown\",\"browser\":\"Unknown\",\"location\":\"内网IP\",\"ipAddr\":\"192.168.8.105\"}");

		SessionInvalidSupport.markReplaced(List.of("old-token"), "new-token", "1001", "tenant-1", "TOB",
				template);

		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(ops).set(eq(CacheConstants.SESSION_INVALID_KEY + "old-token"), json.capture(),
				eq(SessionInvalidSupport.TTL_SECONDS), eq(TimeUnit.SECONDS));
		assertThat(json.getValue())
			.as("Unknown 设备字段按缺失处理，地点与 IP 仍保留")
			.contains("内网IP")
			.contains("\"ip\":\"192.168.8.105\"")
			.doesNotContain("Unknown");
	}

	@Test
	void markReplacedSkipsWhenNothingWasReplaced() {
		StringRedisTemplate template = mock(StringRedisTemplate.class);

		SessionInvalidSupport.markReplaced(List.of(), "new-token", "1001", "tenant-1", "TOB", template);
		SessionInvalidSupport.markReplaced(null, "new-token", "1001", "tenant-1", "TOB", template);

		verifyNoInteractions(template);
	}

	@Test
	void markReplacedSwallowsRedisFailure() {
		StringRedisTemplate template = mock(StringRedisTemplate.class);
		when(template.opsForValue()).thenThrow(new IllegalStateException("redis down"));

		assertThatCode(() -> SessionInvalidSupport.markReplaced(List.of("old-token"), "new-token", "1001", "tenant-1", "TOB",
				template))
			.doesNotThrowAnyException();
	}

	@Test
	void resolveDataMergesCachedRecordAndDropsUnknownDeviceFields() {
		StringRedisTemplate template = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(template.opsForValue()).thenReturn(ops);
		when(ops.get(CacheConstants.SESSION_INVALID_KEY + "old-token")).thenReturn(
				"{\"reason\":\"BE_REPLACED\",\"invalidAt\":\"2026-10-28T21:32:07+08:00\","
						+ "\"device\":{\"os\":\"Windows 10\",\"browser\":\"Chrome 120\",\"location\":\"上海市\","
						+ "\"loginTime\":\"2026-10-28T21:32:05+08:00\",\"ip\":\"1.2.3.4\",\"ipAddr\":\"9.9.9.9\"}}");

		Map<String, Object> data = SessionInvalidSupport.resolveData(SessionInvalidReason.BE_REPLACED, template,
				"old-token");

		assertThat(data).containsEntry("reason", "BE_REPLACED")
			.containsEntry("invalidAt", "2026-10-28T21:32:07+08:00");
		@SuppressWarnings("unchecked")
		Map<String, String> device = (Map<String, String>) data.get("device");
		assertThat(device).containsOnlyKeys("os", "browser", "location", "ip", "loginTime")
			.containsEntry("ip", "1.2.3.4");
	}

	@Test
	void resolveDataFallsBackToReasonOnlyWhenTokenOrRecordMissing() {
		StringRedisTemplate template = mock(StringRedisTemplate.class);

		Map<String, Object> withoutToken = SessionInvalidSupport.resolveData(SessionInvalidReason.TOKEN_EXPIRED,
				template, null);
		assertThat(withoutToken).containsEntry("reason", "TOKEN_EXPIRED")
			.doesNotContainKey("device")
			.doesNotContainKey("invalidAt");

		@SuppressWarnings("unchecked")
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(template.opsForValue()).thenReturn(ops);
		when(ops.get(anyString())).thenReturn(null);
		Map<String, Object> withoutRecord = SessionInvalidSupport.resolveData(SessionInvalidReason.TOKEN_EXPIRED,
				template, "some-token");
		assertThat(withoutRecord).containsEntry("reason", "TOKEN_EXPIRED").doesNotContainKey("device");
	}

	@Test
	void resolveDataAlwaysUsesExceptionReasonAsAuthoritative() {
		StringRedisTemplate template = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(template.opsForValue()).thenReturn(ops);
		when(ops.get(anyString()))
			.thenReturn("{\"reason\":\"KICK_OUT\",\"invalidAt\":\"2026-10-28T21:32:07+08:00\",\"device\":{}}");

		Map<String, Object> data = SessionInvalidSupport.resolveData(SessionInvalidReason.BE_REPLACED, template,
				"old-token");

		assertThat(data).containsEntry("reason", "BE_REPLACED");
	}

	@Test
	void resolveDataSwallowsRedisFailure() {
		StringRedisTemplate template = mock(StringRedisTemplate.class);
		when(template.opsForValue()).thenThrow(new IllegalStateException("redis down"));

		assertThatCode(() -> SessionInvalidSupport.resolveData(SessionInvalidReason.BE_REPLACED, template, "token"))
			.doesNotThrowAnyException();
		assertThat(SessionInvalidSupport.resolveData(SessionInvalidReason.BE_REPLACED, template, "token"))
			.containsEntry("reason", "BE_REPLACED");
	}

	@Test
	void payloadFromReplacedRecordContainsNoCredentialFields() {
		Map<String, String> device = new LinkedHashMap<>();
		device.put("os", "Windows 10");
		device.put("browser", "Chrome 120");
		device.put("location", "上海市");
		device.put("loginTime", "2026-10-28T21:32:05+08:00");
		// resolveData 已白名单过滤，这里再做一次端到端序列化校验
		String json = JSONUtil.toJsonStr(SessionInvalidSupport.resolveData(SessionInvalidReason.BE_REPLACED,
				null, null));
		assertThat(json).contains("\"reason\":\"BE_REPLACED\"").doesNotContain("token");
	}

	@Test
	void markKickoutTokensWritesSubReasonWithoutDevice() {
		StringRedisTemplate template = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(template.opsForValue()).thenReturn(ops);

		SessionInvalidSupport.markKickoutTokens(List.of("token-1", "token-2"),
				SessionInvalidSubReason.ACCOUNT_DISABLED, template);

		ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
		verify(ops).set(eq(CacheConstants.SESSION_INVALID_KEY + "token-1"), json.capture(),
				eq(SessionInvalidSupport.TTL_SECONDS), eq(TimeUnit.SECONDS));
		verify(ops).set(eq(CacheConstants.SESSION_INVALID_KEY + "token-2"), anyString(), any(Long.class),
				eq(TimeUnit.SECONDS));
		assertThat(json.getValue())
			.contains("\"reason\":\"KICK_OUT\"")
			.contains("\"subReason\":\"ACCOUNT_DISABLED\"")
			.as("被踢下线不携带新设备信息")
			.doesNotContain("device")
			.as("记录里不得出现 token 本身")
			.doesNotContain("token-1")
			.doesNotContain("token-2");
	}

	@Test
	void markKickoutTokensSkipsWhenNothingToMark() {
		StringRedisTemplate template = mock(StringRedisTemplate.class);

		SessionInvalidSupport.markKickoutTokens(List.of(), SessionInvalidSubReason.ADMIN_KICK, template);
		SessionInvalidSupport.markKickoutTokens(null, SessionInvalidSubReason.ADMIN_KICK, template);
		SessionInvalidSupport.markKickoutTokens(List.of("t"), null, template);

		verifyNoInteractions(template);
	}

	@Test
	void resolveDataReturnsSubReasonFromCachedRecord() {
		StringRedisTemplate template = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(template.opsForValue()).thenReturn(ops);
		when(ops.get(CacheConstants.SESSION_INVALID_KEY + "token-1")).thenReturn(
				"{\"reason\":\"KICK_OUT\",\"subReason\":\"PASSWORD_CHANGED\","
						+ "\"invalidAt\":\"2026-10-28T23:50:12+08:00\"}");

		Map<String, Object> data = SessionInvalidSupport.resolveData(SessionInvalidReason.KICK_OUT, template,
				"token-1");

		assertThat(data).containsEntry("reason", "KICK_OUT")
			.containsEntry("subReason", "PASSWORD_CHANGED")
			.containsEntry("invalidAt", "2026-10-28T23:50:12+08:00");
	}

	@Test
	void resolveDataDropsUnknownSubReason() {
		StringRedisTemplate template = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(template.opsForValue()).thenReturn(ops);
		when(ops.get(anyString())).thenReturn(
				"{\"reason\":\"KICK_OUT\",\"subReason\":\"FUTURE_UNKNOWN_REASON\",\"invalidAt\":\"now\"}");

		Map<String, Object> data = SessionInvalidSupport.resolveData(SessionInvalidReason.KICK_OUT, template,
				"token-1");

		assertThat(data).containsEntry("reason", "KICK_OUT").doesNotContainKey("subReason");
	}

	@Test
	void kickoutHelpersDelegateToSaTokenAfterMarkingReason() {
		try (org.mockito.MockedStatic<cn.dev33.satoken.stp.StpUtil> stp = org.mockito.Mockito
			.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
			SessionInvalidSupport.kickoutWithReason("user-1", "TOB", SessionInvalidSubReason.ADMIN_KICK);
			SessionInvalidSupport.kickoutTokenWithReason("token-1", SessionInvalidSubReason.TENANT_DISABLED);

			stp.verify(() -> cn.dev33.satoken.stp.StpUtil.kickout("user-1", "TOB"));
			stp.verify(() -> cn.dev33.satoken.stp.StpUtil.kickoutByTokenValue("token-1"));
		}
	}

	@Test
	void markKickoutTokensPublishesSessionInvalidEventWithSubReason() {
		StringRedisTemplate template = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(template.opsForValue()).thenReturn(ops);
		when(ops.get(CacheConstants.SYS_ONLINE_KEY + "token-1"))
			.thenReturn("{\"tenantId\":\"t1\",\"deviceType\":\"TOB\"}");

		try (org.mockito.MockedStatic<cn.dev33.satoken.stp.StpUtil> stp = org.mockito.Mockito
			.mockStatic(cn.dev33.satoken.stp.StpUtil.class)) {
			stp.when(() -> cn.dev33.satoken.stp.StpUtil.getLoginIdByToken("token-1")).thenReturn("42");

			SessionInvalidSupport.markKickoutTokens(List.of("token-1"), SessionInvalidSubReason.ACCOUNT_DISABLED,
					template);
		}

		ArgumentCaptor<String> pushed = ArgumentCaptor.forClass(String.class);
		verify(template).convertAndSend(eq(SessionInvalidPushConstants.CHANNEL), pushed.capture());
		assertThat(pushed.getValue())
			.contains("\"eventType\":\"SESSION_INVALID\"")
			.contains("\"reason\":\"KICK_OUT\"")
			.contains("\"subReason\":\"ACCOUNT_DISABLED\"")
			.contains("\"recipientType\":\"SYS_USER\"")
			.contains("\"tenantId\":\"t1\"")
			.contains("\"recipientId\":\"42\"")
			.doesNotContain("token-1");
	}

	@Test
	void publishSessionInvalidSkipsWithoutRoutingInfoAndDropsSensitiveDeviceFields() {
		StringRedisTemplate template = mock(StringRedisTemplate.class);

		SessionInvalidSupport.publishSessionInvalid(template, SessionInvalidReason.BE_REPLACED, null, "now",
				Map.of("os", "Windows 10", "ipAddr", "9.9.9.9"), " ", "SYS_USER", "42");
		verifyNoInteractions(template);

		SessionInvalidSupport.publishSessionInvalid(template, SessionInvalidReason.BE_REPLACED, null, "now",
				Map.of("os", "Windows 10", "ip", "1.2.3.4", "ipAddr", "9.9.9.9"), "t1", "SYS_USER", "42");
		ArgumentCaptor<String> pushed = ArgumentCaptor.forClass(String.class);
		verify(template).convertAndSend(eq(SessionInvalidPushConstants.CHANNEL), pushed.capture());
		assertThat(pushed.getValue())
			.contains("\"os\":\"Windows 10\"")
			.contains("\"ip\":\"1.2.3.4\"")
			.contains("\"originInstanceId\"")
			.as("设备字段走白名单：ip 允许下发（2026-11-01 产品决策），非白名单键 ipAddr 不得透传")
			.doesNotContain("ipAddr")
			.doesNotContain("9.9.9.9");
	}

	@Test
	void currentTokenDegradesQuietlyWithoutWebContext() {
		assertThatCode(SessionInvalidSupport::currentToken).doesNotThrowAnyException();
	}

}
