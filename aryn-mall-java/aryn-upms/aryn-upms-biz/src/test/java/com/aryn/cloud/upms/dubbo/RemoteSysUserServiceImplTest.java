
package com.aryn.cloud.upms.dubbo;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.entity.SysRole;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.entity.SysUserRole;
import com.aryn.cloud.upms.mapper.SysRoleMapper;
import com.aryn.cloud.upms.mapper.SysUserMapper;
import com.aryn.cloud.upms.mapper.SysUserRoleMapper;
import com.aryn.cloud.upms.service.ISysUserRoleService;
import com.aryn.cloud.upms.service.ISysUserService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 租户侧员工账号查询边界：平台管理员账号不属于任何租户员工视图；
 * 以及配送角色授予/回收幂等与逻辑删除测试（R-06）。
 */
class RemoteSysUserServiceImplTest {

	private ISysUserService sysUserService;

	private SysRoleMapper sysRoleMapper;

	private SysUserRoleMapper sysUserRoleMapper;

	private ISysUserRoleService sysUserRoleService;

	private SysUserMapper sysUserMapper;

	private RemoteSysUserServiceImpl service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		sysUserService = mock(ISysUserService.class);
		sysRoleMapper = mock(SysRoleMapper.class);
		sysUserRoleMapper = mock(SysUserRoleMapper.class);
		sysUserRoleService = mock(ISysUserRoleService.class);
		sysUserMapper = mock(SysUserMapper.class);
		service = new RemoteSysUserServiceImpl(sysUserService, sysRoleMapper, sysUserRoleMapper, sysUserRoleService,
				sysUserMapper);
	}

	private static SysUser user(String id) {
		SysUser user = new SysUser();
		user.setId(id);
		return user;
	}

	@Test
	void getUserByIdHidesPlatformAdminAccount() {
		SysUser system = user("system-1");
		when(sysUserService.getById("system-1")).thenReturn(system);
		when(sysUserMapper.selectPlatformAdminFlag("system-1")).thenReturn("1");

		assertThat(service.getUserById("system-1")).isNull();
		verify(sysUserService, never()).findUserInfo(any(SysUser.class));
	}

	@Test
	void getUserByIdReturnsNormalAccount() {
		SysUser staff = user("user-1");
		when(sysUserService.getById("user-1")).thenReturn(staff);
		when(sysUserMapper.selectPlatformAdminFlag("user-1")).thenReturn("0");
		when(sysUserService.findUserInfo(staff)).thenReturn(staff);

		assertThat(service.getUserById("user-1")).isSameAs(staff);
	}

	@Test
	void getUserInfoByIdsExcludesPlatformAdminAccounts() {
		SysUser staff = user("user-1");
		SysUser system = user("system-1");
		when(sysUserService.listByIds(List.of("user-1", "system-1")))
			.thenReturn(new ArrayList<>(List.of(staff, system)));
		when(sysUserMapper.selectPlatformAdminIds(any())).thenReturn(List.of("system-1"));
		when(sysUserService.findUserInfo(staff)).thenReturn(staff);

		List<SysUser> users = service.getUserInfoByIds(List.of("user-1", "system-1"));

		assertThat(users).extracting(SysUser::getId).containsExactly("user-1");
		verify(sysUserService, never()).findUserInfo(system);
	}

	@Test
	void searchNormalUsersDelegatesToMapperWithClampedLimit() {
		when(sysUserMapper.searchNormalStaff("wqy", 50)).thenReturn(List.of());

		assertThat(service.searchNormalUsers("wqy", 999)).isEmpty();
		verify(sysUserMapper).searchNormalStaff("wqy", 50);
	}

	@Test
	void getPlatformAdminUserIdsNormalizesNullToEmptyList() {
		when(sysUserMapper.selectPlatformAdminIds(any())).thenReturn(null);

		assertThat(service.getPlatformAdminUserIds()).isEmpty();
	}

	private void givenDeliveryRole() {
		SysRole role = new SysRole();
		role.setId("role-1");
		role.setRoleCode("delivery_staff");
		role.setRoleName("配送员");
		when(sysRoleMapper.selectOne(any())).thenReturn(role);
	}

	@Test
	void grantDelegatesToRoleGrantService() {
		givenDeliveryRole();
		assertThat(service.changeRoleByCode("user-1", "delivery_staff", true)).isTrue();
		verify(sysUserRoleService).grantRole("user-1", "role-1");
	}

	@Test
	void grantThrowsWhenRoleMissing() {
		when(sysRoleMapper.selectOne(any())).thenReturn(null);
		assertThatThrownBy(() -> service.changeRoleByCode("user-1", "delivery_staff", true))
			.isInstanceOf(ArynBusinessException.class);
	}

	@Test
	void grantRequiresUserIdAndRoleCode() {
		assertThatThrownBy(() -> service.changeRoleByCode("", "delivery_staff", true))
			.isInstanceOf(ArynBusinessException.class);
	}

	@Test
	void revokeIsIdempotentWhenNoActiveRelation() {
		givenDeliveryRole();
		when(sysUserRoleMapper.selectCount(any())).thenReturn(0L);

		assertThat(service.changeRoleByCode("user-1", "delivery_staff", false)).isTrue();
		verify(sysUserRoleMapper, never()).delete(any(Wrapper.class));
	}

	@Test
	void revokeLogicallyDeletesActiveRelation() {
		givenDeliveryRole();
		when(sysUserRoleMapper.selectCount(any())).thenReturn(1L);

		assertThat(service.changeRoleByCode("user-1", "delivery_staff", false)).isTrue();
		// @TableLogic 下 delete 生成 UPDATE del_flag='1'，历史保留可审计
		verify(sysUserRoleMapper).delete(any(Wrapper.class));
		verify(sysUserRoleService, never()).grantRole(anyString(), anyString());
	}

}
