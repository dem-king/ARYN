package com.aryn.cloud.auth.controller;

import cn.dev33.satoken.session.SaTerminalInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.auth.controller.vo.SessionDeviceVO;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.util.IpUtils;
import com.aryn.cloud.common.core.util.Result;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录设备（在线会话）自助管理：
 * - 只返回当前账号自己的会话；
 * - token 只以 SHA-256 前 12 位出现（可下线定位、不可反推/重放）；
 * - 当前设备禁止下线（走退出登录），其他设备可下线。
 */
class SessionDeviceControllerTest {

	private static final String CURRENT_TOKEN = "token-current";

	private static final String OTHER_TOKEN = "token-other";

	@Test
	void listsOwnSessionsWithMaskedIdsAndCurrentFlag() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(ops);
		when(ops.get(CacheConstants.SYS_ONLINE_KEY + CURRENT_TOKEN)).thenReturn(
				"{\"browser\":\"Chrome 120\",\"os\":\"Windows 10\",\"location\":\"上海市\",\"tenantId\":\"t1\"}");

		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(StpUtil::getLoginId).thenReturn("1001");
			stp.when(StpUtil::getLoginDeviceType).thenReturn("TOB");
			stp.when(StpUtil::getTokenValue).thenReturn(CURRENT_TOKEN);
			stp.when(() -> StpUtil.getTerminalListByLoginId("1001", "TOB"))
				.thenReturn(List.of(
						new SaTerminalInfo().setTokenValue(OTHER_TOKEN).setCreateTime(1000L),
						new SaTerminalInfo().setTokenValue(CURRENT_TOKEN).setCreateTime(2000L)));

			Result<List<SessionDeviceVO>> result = new SessionDeviceController(redisTemplate).devices();

			assertThat(result.getCode()).isZero();
			List<SessionDeviceVO> devices = result.getData();
			assertThat(devices).hasSize(2);
			assertThat(devices.get(0).isCurrent()).as("当前设备排在最前").isTrue();
			assertThat(devices).extracting(SessionDeviceVO::getId)
				.containsExactly(SessionDeviceController.hash(CURRENT_TOKEN),
						SessionDeviceController.hash(OTHER_TOKEN));
			assertThat(devices.get(0).getId()).hasSize(SessionDeviceController.TOKEN_HASH_LENGTH);
			assertThat(devices.get(0).getBrowser()).isEqualTo("Chrome 120");
			assertThat(devices.get(0).getOs()).isEqualTo("Windows 10");
			assertThat(devices.get(0).getLocation()).isEqualTo("上海市");
			assertThat(devices.get(0).getLoginTime()).isNotBlank();
			// 响应中不得出现任何原始 token
			assertThat(JSONUtil.toJsonStr(devices)).doesNotContain(CURRENT_TOKEN).doesNotContain(OTHER_TOKEN);
		}
	}

	@Test
	void resolvesCurrentDeviceLocationFromRequestWhenOnlineRecordIsUnknown() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		@SuppressWarnings("unchecked")
		ValueOperations<String, String> ops = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(ops);
		when(ops.get(CacheConstants.SYS_ONLINE_KEY + CURRENT_TOKEN)).thenReturn(
				"{\"browser\":\"Chrome 120\",\"os\":\"Windows 10\",\"location\":\"未知\",\"ipAddr\":\"2a06:98c0:3600::103\"}");

		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class);
				MockedStatic<IpUtils> ipUtils = mockStatic(IpUtils.class)) {
			stp.when(StpUtil::getLoginId).thenReturn("1001");
			stp.when(StpUtil::getLoginDeviceType).thenReturn("TOB");
			stp.when(StpUtil::getTokenValue).thenReturn(CURRENT_TOKEN);
			stp.when(() -> StpUtil.getTerminalListByLoginId("1001", "TOB"))
				.thenReturn(List.of(new SaTerminalInfo().setTokenValue(CURRENT_TOKEN).setCreateTime(2000L)));
			ipUtils.when(() -> IpUtils.getClientIp(any())).thenReturn("114.114.114.114");
			ipUtils.when(() -> IpUtils.getWhoisAddress("114.114.114.114")).thenReturn("江苏省 南京市");

			Result<List<SessionDeviceVO>> result = new SessionDeviceController(redisTemplate).devices();

			assertThat(result.getData().get(0).getLocation())
				.as("当前设备的历史在线记录地点为未知时，用本次请求 IP 重新解析")
				.isEqualTo("江苏省 南京市");
		}
	}

	@Test
	void rejectsRemovingCurrentDevice() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(StpUtil::getLoginId).thenReturn("1001");
			stp.when(StpUtil::getLoginDeviceType).thenReturn("TOB");
			stp.when(StpUtil::getTokenValue).thenReturn(CURRENT_TOKEN);
			stp.when(() -> StpUtil.getTerminalListByLoginId("1001", "TOB"))
				.thenReturn(List.of(new SaTerminalInfo().setTokenValue(CURRENT_TOKEN).setCreateTime(2000L)));

			Result<Void> result = new SessionDeviceController(redisTemplate)
				.remove(SessionDeviceController.hash(CURRENT_TOKEN));

			assertThat(result.getCode()).isNotZero();
			assertThat(result.getMsg()).contains("当前设备请使用退出登录");
		}
	}

	@Test
	void removesOtherDeviceOfSameAccount() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(StpUtil::getLoginId).thenReturn("1001");
			stp.when(StpUtil::getLoginDeviceType).thenReturn("TOB");
			stp.when(StpUtil::getTokenValue).thenReturn(CURRENT_TOKEN);
			stp.when(() -> StpUtil.getTerminalListByLoginId("1001", "TOB"))
				.thenReturn(List.of(
						new SaTerminalInfo().setTokenValue(CURRENT_TOKEN).setCreateTime(2000L),
						new SaTerminalInfo().setTokenValue(OTHER_TOKEN).setCreateTime(1000L)));

			Result<Void> result = new SessionDeviceController(redisTemplate)
				.remove(SessionDeviceController.hash(OTHER_TOKEN));

			assertThat(result.getCode()).isZero();
			stp.verify(() -> StpUtil.kickoutByTokenValue(OTHER_TOKEN));
			verify(redisTemplate).delete(CacheConstants.SYS_ONLINE_KEY + OTHER_TOKEN);
		}
	}

	@Test
	void rejectsUnknownDeviceId() {
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(StpUtil::getLoginId).thenReturn("1001");
			stp.when(StpUtil::getLoginDeviceType).thenReturn("TOB");
			stp.when(StpUtil::getTokenValue).thenReturn(CURRENT_TOKEN);
			stp.when(() -> StpUtil.getTerminalListByLoginId("1001", "TOB"))
				.thenReturn(List.of(new SaTerminalInfo().setTokenValue(CURRENT_TOKEN).setCreateTime(2000L)));

			Result<Void> result = new SessionDeviceController(redisTemplate).remove("deadbeef0000");

			assertThat(result.getCode()).isNotZero();
			assertThat(result.getMsg()).contains("不属于当前账号");
			assertThat(redisTemplate.getConnectionFactory()).isNull();
			verify(redisTemplate, org.mockito.Mockito.never()).delete(anyString());
		}
	}

}
