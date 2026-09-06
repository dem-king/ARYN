
package com.aryn.cloud.upms.dubbo;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.entity.SysRole;
import com.aryn.cloud.upms.api.entity.SysUserRole;
import com.aryn.cloud.upms.mapper.SysRoleMapper;
import com.aryn.cloud.upms.mapper.SysUserRoleMapper;
import com.aryn.cloud.upms.service.ISysUserRoleService;
import com.aryn.cloud.upms.service.ISysUserService;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
 * 配送角色授予/回收幂等与逻辑删除测试（R-06）
 */
class RemoteSysUserServiceImplTest {

	private ISysUserService sysUserService;

	private SysRoleMapper sysRoleMapper;

	private SysUserRoleMapper sysUserRoleMapper;

	private ISysUserRoleService sysUserRoleService;

	private RemoteSysUserServiceImpl service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		sysUserService = mock(ISysUserService.class);
		sysRoleMapper = mock(SysRoleMapper.class);
		sysUserRoleMapper = mock(SysUserRoleMapper.class);
		sysUserRoleService = mock(ISysUserRoleService.class);
		service = new RemoteSysUserServiceImpl(sysUserService, sysRoleMapper, sysUserRoleMapper, sysUserRoleService);
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
