package com.aryn.cloud.upms.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.enums.SessionInvalidSubReason;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.entity.SysDept;
import com.aryn.cloud.upms.api.entity.SysRole;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.entity.SysUserRole;
import com.aryn.cloud.upms.mapper.SysDeptMapper;
import com.aryn.cloud.upms.mapper.SysMenuMapper;
import com.aryn.cloud.upms.mapper.SysRoleMapper;
import com.aryn.cloud.upms.mapper.SysRoleMenuMapper;
import com.aryn.cloud.upms.mapper.SysTenantMapper;
import com.aryn.cloud.upms.mapper.SysUserMapper;
import com.aryn.cloud.upms.mapper.SysUserRoleMapper;
import com.aryn.cloud.upms.service.ISysUserRoleService;
import com.aryn.cloud.upms.support.PlatformAdminSupport;
import com.aryn.cloud.upms.support.SysUserSessionRevoker;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

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
 * 通用用户新增/编辑不允许直接授予或回收配送资格，编辑时必须保留已有配送角色关联；
 * 平台管理员账号对任何角色账号不可见，权限恒为全部可见菜单。
 */
class SysUserServiceImplTest {

	private static final String PROTECTED_ROLE_ID = "role-delivery-staff";

	private static final String NORMAL_ROLE_ID = "role-staff";

	private SysUserMapper sysUserMapper;

	private SysRoleMapper sysRoleMapper;

	private SysMenuMapper sysMenuMapper;

	private SysTenantMapper sysTenantMapper;

	private SysUserRoleMapper sysUserRoleMapper;

	private ISysUserRoleService sysUserRoleService;

	private SysUserSessionRevoker sysUserSessionRevoker;

	private SysDeptMapper sysDeptMapper;

	private SysUserServiceImpl service;

	@BeforeEach
	void setUp() {
		sysUserMapper = mock(SysUserMapper.class);
		sysRoleMapper = mock(SysRoleMapper.class);
		sysMenuMapper = mock(SysMenuMapper.class);
		sysTenantMapper = mock(SysTenantMapper.class);
		sysUserRoleMapper = mock(SysUserRoleMapper.class);
		sysUserRoleService = mock(ISysUserRoleService.class);
		sysUserSessionRevoker = mock(SysUserSessionRevoker.class);
		sysDeptMapper = mock(SysDeptMapper.class);
		service = new SysUserServiceImpl(sysRoleMapper, sysMenuMapper, sysUserRoleMapper,
				sysUserRoleService, mock(SysRoleMenuMapper.class), sysTenantMapper,
				sysUserSessionRevoker, new PlatformAdminSupport(), sysDeptMapper);
		ReflectionTestUtils.setField(service, "baseMapper", sysUserMapper);
		when(sysUserRoleService.grantRole(anyString(), anyString())).thenReturn(Boolean.TRUE);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
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

	@Test
	void updateUserDisablingAccountRevokesExistingSessions() {
		// 账号从正常改为停用：已签发 token 仍有效，必须立即撤销该账号全部会话
		mockProtectedRole();
		when(sysUserRoleMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of());
		when(sysUserMapper.selectById("user-1")).thenReturn(account("user-1", "0"));
		when(sysUserMapper.updateById(any(SysUser.class))).thenReturn(1);
		SysUser user = new SysUser();
		user.setId("user-1");
		user.setStatus("1");
		user.setRoles(java.util.List.of(NORMAL_ROLE_ID));

		assertThat(service.updateUser(user)).isTrue();
		verify(sysUserSessionRevoker).revokeAll("user-1", SessionInvalidSubReason.ACCOUNT_DISABLED);
	}

	@Test
	void updateUserEditingNormalAccountDoesNotRevokeSessions() {
		// 普通编辑（状态不变/未提交状态）不能误踢在线会话
		mockProtectedRole();
		when(sysUserRoleMapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of());
		when(sysUserMapper.selectById("user-1")).thenReturn(account("user-1", "0"));
		when(sysUserMapper.updateById(any(SysUser.class))).thenReturn(1);
		SysUser user = new SysUser();
		user.setId("user-1");
		user.setStatus("0");
		user.setRoles(java.util.List.of(NORMAL_ROLE_ID));

		assertThat(service.updateUser(user)).isTrue();
		verify(sysUserSessionRevoker, never()).revokeAll(anyString(), any());
	}

	@Test
	void delUserRevokesSessionsOfDeletedAccount() {
		when(sysUserMapper.deleteById(any(java.io.Serializable.class))).thenReturn(1);
		SysUser user = account("user-1", "0");

		assertThat(service.delUser(user)).isTrue();
		verify(sysUserSessionRevoker).revokeAll("user-1", SessionInvalidSubReason.ACCOUNT_DELETED);
	}

	@Test
	void visibleUserLookupHidesPlatformAdminAccount() {
		SysUser system = account("system-1", "0");
		when(sysUserMapper.selectById("system-1")).thenReturn(system);
		when(sysUserMapper.selectPlatformAdminFlag("system-1")).thenReturn("1");

		assertThat(service.getVisibleUserById("system-1")).isNull();
	}

	@Test
	void visibleUserLookupReturnsNormalAccount() {
		SysUser staff = account("user-1", "0");
		when(sysUserMapper.selectById("user-1")).thenReturn(staff);
		when(sysUserMapper.selectPlatformAdminFlag("user-1")).thenReturn("0");

		assertThat(service.getVisibleUserById("user-1")).isSameAs(staff);
	}

	@Test
	void countVisibleStaffExcludesPlatformAdmin() {
		// 租户概览的「管理端账号」= 租户员工数，平台管理员不属于任何租户
		when(sysUserMapper.countTenantStaff()).thenReturn(2L);

		assertThat(service.countVisibleStaff()).isEqualTo(2L);
		verify(sysUserMapper).countTenantStaff();
	}

	@Test
	void platformAdminPermissionsComeFromAllMenusNotRoleGrants() {		// 平台管理员角色已冻结、不再经角色管理维护；权限必须由「全部可见菜单」动态推导
		SysTenant tenant = new SysTenant();
		tenant.setId("tenant-platform");
		tenant.setTenantType(CommonConstants.TENANT_TYPE_PLATFORM);
		when(sysTenantMapper.selectById("tenant-platform")).thenReturn(tenant);
		when(sysUserMapper.selectPlatformAdminFlag("system-1")).thenReturn("1");
		when(sysRoleMapper.listRoleIdsByUserId("system-1")).thenReturn(List.of("role-platform-admin"));
		when(sysMenuMapper.selectAllPermissions()).thenReturn(List.of("upms:sysrole:page", "upms:sysuser:page"));
		SysUser user = new SysUser();
		user.setId("system-1");
		user.setTenantId("tenant-platform");

		SysUser result = service.findUserInfo(user);

		assertThat(result.getPlatformAdmin()).isTrue();
		assertThat(result.getPermissions()).containsExactlyInAnyOrder("upms:sysrole:page", "upms:sysuser:page");
		verify(sysMenuMapper, never()).listMenuByRoleId(anyString());
	}

	@Test
	void findUserInfoFillsDeptNameFromDeptTable() {
		// deptName 是 sys_dept 的关联展示字段，按 ID 查 sys_user 不会带出；
		// 远程员工查询依赖它给销售员等下游业务快照部门，必须一并回填。
		SysUser user = new SysUser();
		user.setId("user-1");
		user.setTenantId("tenant-1");
		user.setDeptId("dept-sales");
		SysDept dept = new SysDept();
		dept.setId("dept-sales");
		dept.setDeptName("销售部");
		when(sysDeptMapper.selectById("dept-sales")).thenReturn(dept);
		when(sysRoleMapper.listRoleIdsByUserId("user-1")).thenReturn(List.of());

		SysUser result = service.findUserInfo(user);

		assertThat(result.getDeptName()).isEqualTo("销售部");
	}

	@Test
	void findUserInfoSkipsDeptLookupWithoutDeptId() {
		SysUser user = new SysUser();
		user.setId("user-2");
		user.setTenantId("tenant-1");
		when(sysRoleMapper.listRoleIdsByUserId("user-2")).thenReturn(List.of());

		SysUser result = service.findUserInfo(user);

		assertThat(result.getDeptName()).isNull();
		verify(sysDeptMapper, never()).selectById(anyString());
	}

	private static SysUser account(String id, String status) {
		SysUser user = new SysUser();
		user.setId(id);
		user.setStatus(status);
		return user;
	}

}
