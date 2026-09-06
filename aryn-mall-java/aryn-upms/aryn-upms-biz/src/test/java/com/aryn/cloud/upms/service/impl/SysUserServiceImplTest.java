package com.aryn.cloud.upms.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.entity.SysRole;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.entity.SysUserRole;
import com.aryn.cloud.upms.mapper.SysMenuMapper;
import com.aryn.cloud.upms.mapper.SysRoleMapper;
import com.aryn.cloud.upms.mapper.SysRoleMenuMapper;
import com.aryn.cloud.upms.mapper.SysUserMapper;
import com.aryn.cloud.upms.mapper.SysUserRoleMapper;
import com.aryn.cloud.upms.service.ISysUserRoleService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 员工账号受保护角色（delivery_staff）服务端保护测试：
 * 通用用户新增/编辑不允许直接授予或回收配送资格，编辑时必须保留已有配送角色关联。
 */
class SysUserServiceImplTest {

	private static final String PROTECTED_ROLE_ID = "role-delivery-staff";

	private static final String NORMAL_ROLE_ID = "role-staff";

	private SysUserMapper sysUserMapper;

	private SysRoleMapper sysRoleMapper;

	private SysUserRoleMapper sysUserRoleMapper;

	private ISysUserRoleService sysUserRoleService;

	private SysUserServiceImpl service;

	@BeforeEach
	void setUp() {
		sysUserMapper = mock(SysUserMapper.class);
		sysRoleMapper = mock(SysRoleMapper.class);
		sysUserRoleMapper = mock(SysUserRoleMapper.class);
		sysUserRoleService = mock(ISysUserRoleService.class);
		service = new SysUserServiceImpl(sysRoleMapper, mock(SysMenuMapper.class), sysUserRoleMapper,
				sysUserRoleService, mock(SysRoleMenuMapper.class));
		ReflectionTestUtils.setField(service, "baseMapper", sysUserMapper);
		when(sysUserRoleService.grantRole(anyString(), anyString())).thenReturn(Boolean.TRUE);
	}

	private void mockProtectedRole() {
		SysRole protectedRole = new SysRole();
		protectedRole.setId(PROTECTED_ROLE_ID);
		protectedRole.setRoleCode("delivery_staff");
		when(sysRoleMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of(protectedRole));
	}

	@Test
	void saveUserRejectsDirectDeliveryRoleGrant() {
		mockProtectedRole();
		SysUser user = new SysUser();
		user.setUsername("new-staff");
		user.setRoles(java.util.List.of(NORMAL_ROLE_ID, PROTECTED_ROLE_ID));

		assertThatThrownBy(() -> service.saveUser(user))
			.isInstanceOfSatisfying(ArynBusinessException.class,
				e -> assertThat(e.getMsg()).contains("配送员管理"));
		verify(sysUserMapper, never()).insert(any(SysUser.class));
	}

	@Test
	void saveUserAllowsNormalRolesOnly() {
		mockProtectedRole();
		when(sysUserMapper.selectUserByName("new-staff")).thenReturn(null);
		when(sysUserMapper.insert(any(SysUser.class))).thenReturn(1);
		SysUser user = new SysUser();
		user.setUsername("new-staff");
		user.setId("user-1");
		user.setRoles(java.util.List.of(NORMAL_ROLE_ID));

		assertThat(service.saveUser(user)).isTrue();
		verify(sysUserRoleService).grantRole("user-1", NORMAL_ROLE_ID);
	}

	@Test
	void updateUserPreservesExistingProtectedRole() {
		// 前端隐藏配送角色复选框后仅提交普通角色，服务端必须保留已有配送资格关联
		mockProtectedRole();
		SysUserRole protectedLink = new SysUserRole();
		protectedLink.setUserId("user-1");
		protectedLink.setRoleId(PROTECTED_ROLE_ID);
		when(sysUserRoleMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of(protectedLink));
		when(sysUserMapper.updateById(any(SysUser.class))).thenReturn(1);
		SysUser user = new SysUser();
		user.setId("user-1");
		user.setRoles(java.util.List.of(NORMAL_ROLE_ID));

		assertThat(service.updateUser(user)).isTrue();
		verify(sysUserRoleService).grantRole("user-1", NORMAL_ROLE_ID);
		verify(sysUserRoleService).grantRole("user-1", PROTECTED_ROLE_ID);
	}

	@Test
	void updateUserStripsIncomingProtectedRole() {
		// 请求中夹带受保护角色时不授予也不回收，仅按普通角色重建关联
		mockProtectedRole();
		when(sysUserRoleMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of());
		when(sysUserMapper.updateById(any(SysUser.class))).thenReturn(1);
		SysUser user = new SysUser();
		user.setId("user-1");
		user.setRoles(new java.util.ArrayList<>(java.util.List.of(NORMAL_ROLE_ID, PROTECTED_ROLE_ID)));

		assertThat(service.updateUser(user)).isTrue();
		verify(sysUserRoleService).grantRole("user-1", NORMAL_ROLE_ID);
		verify(sysUserRoleService, never()).grantRole("user-1", PROTECTED_ROLE_ID);
	}

	@Test
	void updateUserFailsWhenNoRoleRemains() {
		// 仅剩受保护角色且无已有配送资格时拒绝保存，避免产生无角色员工账号
		mockProtectedRole();
		when(sysUserRoleMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of());
		SysUser user = new SysUser();
		user.setId("user-1");
		user.setRoles(java.util.List.of(PROTECTED_ROLE_ID));

		assertThatThrownBy(() -> service.updateUser(user))
			.isInstanceOfSatisfying(ArynBusinessException.class,
				e -> assertThat(e.getMsg()).contains("角色不能为空"));
	}

	@Test
	void updateUserRebuildsOnlyNonProtectedLinks() {
		// 存在受保护角色时仍需重建非受保护关联，删除动作只执行一次
		mockProtectedRole();
		when(sysUserRoleMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of());
		when(sysUserMapper.updateById(any(SysUser.class))).thenReturn(1);
		SysUser user = new SysUser();
		user.setId("user-1");
		user.setRoles(java.util.List.of(NORMAL_ROLE_ID));

		assertThat(service.updateUser(user)).isTrue();
		verify(sysUserRoleMapper).delete(any(Wrapper.class));
	}

}
