package com.aryn.cloud.upms.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.dto.SysTenantMenuDTO;
import com.aryn.cloud.upms.mapper.SysMenuMapper;
import com.aryn.cloud.upms.mapper.SysTenantMenuMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 租户分配菜单契约：平台应用菜单不可分配给租户。
 *
 * <p>前端菜单树把平台应用节点置灰只是提示，直接调接口仍然能提交，
 * 因此保存时必须按 applicationKey 自行拦截，且拦截要在删除旧授权之前。
 */
class SysTenantMenuServiceImplTest {

	private static final String TENANT_ID = "tenant-1";

	private SysMenuMapper sysMenuMapper;

	private SysTenantMenuMapper sysTenantMenuMapper;

	private SysTenantMenuServiceImpl service;

	@BeforeEach
	void setUp() {
		sysMenuMapper = mock(SysMenuMapper.class);
		sysTenantMenuMapper = mock(SysTenantMenuMapper.class);
		service = new SysTenantMenuServiceImpl(sysMenuMapper);
		ReflectionTestUtils.setField(service, "baseMapper", sysTenantMenuMapper);
		ArynTenantContextHolder.setTenantId(TENANT_ID);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void saveRejectsPlatformMenus() {
		when(sysMenuMapper.selectCount(any(Wrapper.class))).thenReturn(1L);

		assertThatThrownBy(() -> service.saveTenantMenu(assign("menu-sys")))
			.isInstanceOfSatisfying(ArynBusinessException.class, exception -> assertThat(exception.getMsg()).contains("平台应用菜单不可分配给租户"));
		verify(sysTenantMenuMapper, never()).delete(any(Wrapper.class));
	}

	private static SysTenantMenuDTO assign(String... menuIds) {
		SysTenantMenuDTO dto = new SysTenantMenuDTO();
		dto.setTenantId(TENANT_ID);
		dto.setMenuIds(List.of(menuIds));
		return dto;
	}

}
