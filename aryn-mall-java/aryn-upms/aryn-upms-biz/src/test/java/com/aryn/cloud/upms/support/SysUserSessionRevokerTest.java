package com.aryn.cloud.upms.support;

import cn.dev33.satoken.stp.StpUtil;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mockStatic;

/**
 * 员工账号会话撤销：管理端/配送端同为 TOB，停用、改密、删除后必须立即踢线。
 */
class SysUserSessionRevokerTest {

	private final SysUserSessionRevoker revoker = new SysUserSessionRevoker();

	@Test
	void revokesAllTobSessionsOfTheAccount() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			revoker.revokeAll("user-1", "停用账号");

			stp.verify(() -> StpUtil.kickout("user-1", DeviceTypeEnum.TOB.getDevice()));
		}
	}

	@Test
	void ignoresBlankUserId() {
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			revoker.revokeAll("  ", "停用账号");

			stp.verifyNoInteractions();
		}
	}

	@Test
	void kickoutFailureDoesNotBreakMainFlow() {
		// 无活跃会话时 Sa-Token 会抛异常，账号状态变更不能因此失败/回滚
		try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
			stp.when(() -> StpUtil.kickout("user-1", DeviceTypeEnum.TOB.getDevice()))
				.thenThrow(new IllegalStateException("无活跃会话"));

			assertThatCode(() -> revoker.revokeAll("user-1", "修改密码")).doesNotThrowAnyException();
		}
	}

}
