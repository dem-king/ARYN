package com.aryn.cloud.upms.controller;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.upms.api.entity.SysRole;
import com.aryn.cloud.upms.service.ISysRoleMenuService;
import com.aryn.cloud.upms.service.ISysRoleService;
import com.aryn.cloud.upms.service.ISysUserRoleService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色管理受保护角色测试：delivery_staff 不可删除、不可改码；
 * 配送资格只能经配送员管理（资格接口/向导）授予或回收。
 */
class SysRoleControllerProtectionTest {

	private static final String PROTECTED_ROLE_ID = "6444c80ddec04d02e3e17888fc2f6fed";

	private ISysRoleService sysRoleService;

	private ISysUserRoleService sysUserRoleService;

	private ISysRoleMenuService sysRoleMenuService;

	private SysRoleController controller;

	@BeforeEach
	void setUp() {
		sysRoleService = mock(ISysRoleService.class);
		sysUserRoleService = mock(ISysUserRoleService.class);
		sysRoleMenuService = mock(ISysRoleMenuService.class);
		controller = new SysRoleController(sysRoleService, sysUserRoleService, sysRoleMenuService);
	}

	private SysRole protectedRole() {
		SysRole role = new SysRole();
		role.setId(PROTECTED_ROLE_ID);
		role.setRoleName("配送员");
		role.setRoleCode("delivery_staff");
		return role;
	}

	@Test
	void deleteRejectedForProtectedDeliveryRole() {
		when(sysRoleService.getById(PROTECTED_ROLE_ID)).thenReturn(protectedRole());

		Result result = controller.del(PROTECTED_ROLE_ID);

		assertThat(result.getCode()).isNotEqualTo(0);
		assertThat(result.getMsg()).contains("配送员资格角色不可删除");
		// 未触达绑定校验与删除动作，角色菜单关联保持原样
		verify(sysUserRoleService, never()).count(any());
		verify(sysRoleService, never()).removeById(any(SysRole.class));
		verify(sysRoleMenuService, never()).remove(any(Wrapper.class));
	}

	@Test
	void deleteProceedsForNormalUnboundRole() {
		SysRole normal = new SysRole();
		normal.setId("role-normal");
		normal.setRoleName("运营");
		normal.setRoleCode("ROLE_OPERATOR");
		when(sysRoleService.getById("role-normal")).thenReturn(normal);
		when(sysUserRoleService.count(any())).thenReturn(0L);
		when(sysRoleMenuService.remove(any(Wrapper.class))).thenReturn(true);
		when(sysRoleService.removeById("role-normal")).thenReturn(true);

		Result result = controller.del("role-normal");

		assertThat(result.getCode()).isEqualTo(0);
		verify(sysRoleService).removeById("role-normal");
	}

	@Test
	void editRejectedWhenChangingProtectedRoleCode() {
		when(sysRoleService.getById(PROTECTED_ROLE_ID)).thenReturn(protectedRole());
		SysRole payload = protectedRole();
		payload.setRoleCode("delivery_staff_v2");

		Result result = controller.edit(payload);

		assertThat(result.getCode()).isNotEqualTo(0);
		assertThat(result.getMsg()).contains("角色编码不可修改");
		verify(sysRoleService, never()).updateById(any(SysRole.class));
	}

	@Test
	void editProceedsWhenProtectedRoleCodeUnchanged() {
		when(sysRoleService.getById(PROTECTED_ROLE_ID)).thenReturn(protectedRole());
		when(sysRoleService.checkRole(any(SysRole.class))).thenReturn(false);
		when(sysRoleService.updateById(any(SysRole.class))).thenReturn(true);
		SysRole payload = protectedRole();
		payload.setRoleName("配送员");

		Result result = controller.edit(payload);

		assertThat(result.getCode()).isEqualTo(0);
		verify(sysRoleService).updateById(any(SysRole.class));
	}

}
