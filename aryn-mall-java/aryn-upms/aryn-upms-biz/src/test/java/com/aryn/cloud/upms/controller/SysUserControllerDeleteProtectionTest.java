package com.aryn.cloud.upms.controller;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.order.api.remote.RemoteDeliveryAccountService;
import com.aryn.cloud.upms.api.entity.SysRole;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.service.ISysRoleService;
import com.aryn.cloud.upms.service.ISysUserRoleService;
import com.aryn.cloud.upms.service.ISysUserService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 员工账号删除保护测试：仍关联配送员资料时拒绝删除；远程检查失败时 fail-closed。
 */
class SysUserControllerDeleteProtectionTest {

	private ISysUserService sysUserService;

	private ISysRoleService sysRoleService;

	private ISysUserRoleService sysUserRoleService;

	private RemoteDeliveryAccountService remoteDeliveryAccountService;

	private SysUserController controller;

	@BeforeEach
	void setUp() {
		sysUserService = mock(ISysUserService.class);
		sysRoleService = mock(ISysRoleService.class);
		sysUserRoleService = mock(ISysUserRoleService.class);
		remoteDeliveryAccountService = mock(RemoteDeliveryAccountService.class);
		controller = new SysUserController(sysUserService, sysRoleService, sysUserRoleService,
				remoteDeliveryAccountService);
		SysUser existing = new SysUser();
		existing.setId("user-1");
		when(sysUserService.getById("user-1")).thenReturn(existing);
		SysRole adminRole = new SysRole();
		adminRole.setId("role-admin");
		when(sysRoleService.getOne(any())).thenReturn(adminRole);
		when(sysUserRoleService.count(any())).thenReturn(0L);
	}

	@Test
	void deleteRejectedWhenDeliveryStaffExists() {
		when(remoteDeliveryAccountService.hasActiveDeliveryStaff("user-1")).thenReturn(true);

		Result result = controller.del("user-1");

		assertThat(result.getCode()).isNotEqualTo(0);
		assertThat(result.getMsg()).contains("配送员管理");
		verify(sysUserService, org.mockito.Mockito.never()).delUser(any(SysUser.class));
	}

	@Test
	void deleteFailClosedWhenRemoteCheckUnavailable() {
		// Cloud 模式 Dubbo 不可用或异常时禁止删除，不返回删除成功
		when(remoteDeliveryAccountService.hasActiveDeliveryStaff("user-1"))
			.thenThrow(new RuntimeException("dubbo unavailable"));

		Result result = controller.del("user-1");

		assertThat(result.getCode()).isNotEqualTo(0);
		assertThat(result.getMsg()).contains("无法确认配送关联");
		verify(sysUserService, org.mockito.Mockito.never()).delUser(any(SysUser.class));
	}

	@Test
	void deleteProceedsWhenNoDeliveryStaff() {
		when(remoteDeliveryAccountService.hasActiveDeliveryStaff("user-1")).thenReturn(false);
		when(sysUserService.delUser(any(SysUser.class))).thenReturn(true);

		Result result = controller.del("user-1");

		assertThat(result.getCode()).isEqualTo(0);
		verify(sysUserService).delUser(any(SysUser.class));
	}

}
