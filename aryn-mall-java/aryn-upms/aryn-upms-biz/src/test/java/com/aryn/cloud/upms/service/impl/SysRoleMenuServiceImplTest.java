package com.aryn.cloud.upms.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.dto.SysRoleMenuDTO;
import com.aryn.cloud.upms.api.entity.SysRole;
import com.aryn.cloud.upms.mapper.SysMenuMapper;
import com.aryn.cloud.upms.mapper.SysRoleMapper;
import com.aryn.cloud.upms.mapper.SysTenantMenuMapper;
import com.aryn.cloud.upms.support.PlatformAdminSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 角色菜单授权保护测试：平台管理员角色是系统内置角色，任何会话都不能改写它的菜单授权数据。
 */
class SysRoleMenuServiceImplTest {

	private static final String TENANT_ID = "1590229800633634816";

	private SysRoleMapper sysRoleMapper;

	private SysRoleMenuServiceImpl service;

	@BeforeEach
	void setUp() {
		sysRoleMapper = mock(SysRoleMapper.class);
		service = new SysRoleMenuServiceImpl(sysRoleMapper, mock(SysMenuMapper.class),
				mock(SysTenantMenuMapper.class), new PlatformAdminSupport());
		ArynTenantContextHolder.setTenantId(TENANT_ID);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void saveRoleMenuRejectedForSystemBuiltinPlatformRole() {
		SysRole platformRole = new SysRole();
		platformRole.setId("role-platform-admin");
		platformRole.setRoleCode(CommonConstants.ROLE_PLATFORM_ADMIN_CODE);
		when(sysRoleMapper.selectById("role-platform-admin")).thenReturn(platformRole);
		SysRoleMenuDTO request = new SysRoleMenuDTO();
		request.setRoleId("role-platform-admin");
		request.setMenuIds(List.of());

		assertThatThrownBy(() -> service.saveRoleMenu(request))
			.isInstanceOfSatisfying(ArynBusinessException.class,
				e -> assertThat(e.getMsg()).contains("系统内置角色"));
	}

}
