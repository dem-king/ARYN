package com.aryn.cloud.upms.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.upms.api.entity.SysUserOnline;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * 在线用户列表可见性：key 改为 token 维度后，租户必须按记录值过滤（不能跨租户泄露），
 * 已注销/被顶号的失效会话不能继续展示，平台管理员账号对普通角色账号不可见。
 */
class SysOnlineUserControllerTest {

	private static final Set<String> NO_HIDDEN_USERS = Set.of();

	@Test
	void hidesRecordWhenTenantDoesNotMatch() {
		SysUserOnline online = online("token-1", "tenant-A");

		assertThat(SysOnlineUserController.isDisplayable(online, "tenant-B", NO_HIDDEN_USERS)).isFalse();
	}

	@Test
	void hidesEverythingWhenTenantContextIsMissing() {
		// 旧实现按租户前缀扫描，租户上下文为空时不会返回任何记录；新实现保持同样语义
		SysUserOnline online = online("token-1", "tenant-A");

		assertThat(SysOnlineUserController.isDisplayable(online, null, NO_HIDDEN_USERS)).isFalse();
		assertThat(SysOnlineUserController.isDisplayable(online, " ", NO_HIDDEN_USERS)).isFalse();
	}

	@Test
	void hidesRecordWhenSessionIsNoLongerValid() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			// 默认返回 null：token 已注销/被强退/被顶号
			assertThat(
					SysOnlineUserController.isDisplayable(online("token-1", "tenant-A"), "tenant-A", NO_HIDDEN_USERS))
				.isFalse();
		}
	}

	@Test
	void showsActiveSessionInSameTenant() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(() -> StpUtil.getLoginIdByToken("token-1")).thenReturn("user-1");

			assertThat(
					SysOnlineUserController.isDisplayable(online("token-1", "tenant-A"), "tenant-A", NO_HIDDEN_USERS))
				.isTrue();
		}
	}

	@Test
	void hidesMallSessionFromAdminOnlineList() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(() -> StpUtil.getLoginIdByToken("token-1")).thenReturn("user-1");
			SysUserOnline online = online("token-1", "tenant-A");
			online.setDeviceType(DeviceTypeEnum.TOC.getDevice());

			assertThat(SysOnlineUserController.isDisplayable(online, "tenant-A", NO_HIDDEN_USERS)).isFalse();
		}
	}

	@Test
	void hidesPlatformAdminSessionFromTenantAdmin() {
		// 平台管理员账号是系统内置账号：普通角色账号在线列表不应出现它
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(() -> StpUtil.getLoginIdByToken("token-system")).thenReturn("system-user");
			SysUserOnline online = online("token-system", "tenant-A");
			online.setUserName("system");

			assertThat(SysOnlineUserController.isDisplayable(online, "tenant-A", Set.of("system"))).isFalse();
		}
	}

	@Test
	void showsPlatformAdminSessionToPlatformAdmin() {
		// 平台管理员会话的 hidden 集合为空，因此仍能看到自己的在线记录
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(() -> StpUtil.getLoginIdByToken("token-system")).thenReturn("system-user");
			SysUserOnline online = online("token-system", "tenant-A");
			online.setUserName("system");

			assertThat(SysOnlineUserController.isDisplayable(online, "tenant-A", NO_HIDDEN_USERS)).isTrue();
		}
	}

	private static SysUserOnline online(String tokenId, String tenantId) {
		SysUserOnline online = new SysUserOnline();
		online.setTokenId(tokenId);
		online.setTenantId(tenantId);
		return online;
	}

}
