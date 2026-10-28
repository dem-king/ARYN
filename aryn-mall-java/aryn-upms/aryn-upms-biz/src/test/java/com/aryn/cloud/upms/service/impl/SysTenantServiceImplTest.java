package com.aryn.cloud.upms.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.entity.SysMenu;
import com.aryn.cloud.upms.api.entity.SysRole;
import com.aryn.cloud.upms.api.entity.SysRoleMenu;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.entity.SysTenantMenu;
import com.aryn.cloud.upms.api.entity.SysTenantPackage;
import com.aryn.cloud.upms.mapper.SysTenantMapper;
import com.aryn.cloud.upms.service.ISysDeptService;
import com.aryn.cloud.upms.service.ISysMenuService;
import com.aryn.cloud.upms.service.ISysRoleMenuService;
import com.aryn.cloud.upms.service.ISysRoleService;
import com.aryn.cloud.upms.service.ISysTenantMenuService;
import com.aryn.cloud.upms.service.ISysTenantPackageService;
import com.aryn.cloud.upms.service.ISysUserRoleService;
import com.aryn.cloud.upms.service.ISysUserService;
import com.aryn.cloud.upms.support.TenantSessionRevoker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 租户生命周期与套餐变更测试：套餐变更后菜单和角色授权必须同步裁剪，
 * 停用租户必须撤销存量会话。
 */
class SysTenantServiceImplTest {

	private static final String TENANT_ID = "tenant-1";

	private static final String OLD_PACKAGE_ID = "pkg-old";

	private static final String NEW_PACKAGE_ID = "pkg-new";

	private SysTenantMapper sysTenantMapper;

	private ISysRoleService sysRoleService;

	private ISysMenuService sysMenuService;

	private ISysRoleMenuService sysRoleMenuService;

	private ISysTenantPackageService sysTenantPackageService;

	private ISysTenantMenuService sysTenantMenuService;

	private TenantSessionRevoker tenantSessionRevoker;

	private SysTenantServiceImpl service;

	@BeforeEach
	void setUp() {
		sysTenantMapper = mock(SysTenantMapper.class);
		sysRoleService = mock(ISysRoleService.class);
		sysMenuService = mock(ISysMenuService.class);
		sysRoleMenuService = mock(ISysRoleMenuService.class);
		sysTenantPackageService = mock(ISysTenantPackageService.class);
		sysTenantMenuService = mock(ISysTenantMenuService.class);
		tenantSessionRevoker = mock(TenantSessionRevoker.class);
		CacheManager cacheManager = mock(CacheManager.class);
		service = new SysTenantServiceImpl(sysRoleService, mock(ISysDeptService.class), mock(ISysUserService.class),
				mock(ISysUserRoleService.class), sysMenuService, sysRoleMenuService, sysTenantPackageService,
				sysTenantMenuService, tenantSessionRevoker, cacheManager);
		ReflectionTestUtils.setField(service, "baseMapper", sysTenantMapper);
		when(cacheManager.getCache(CacheConstants.MENU_CACHE)).thenReturn(mock(Cache.class));
		when(sysTenantMapper.updateById(any(SysTenant.class))).thenReturn(1);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void packageChangeResyncsTenantMenusAndRoleGrants() {
		SysTenant existing = tenant(OLD_PACKAGE_ID, CommonConstants.NORMAL_STATUS);
		SysTenant updated = tenant(NEW_PACKAGE_ID, CommonConstants.NORMAL_STATUS);
		when(sysTenantMapper.selectById(TENANT_ID)).thenReturn(existing, updated);

		SysTenantPackage newPackage = new SysTenantPackage();
		newPackage.setId(NEW_PACKAGE_ID);
		newPackage.setStatus(CommonConstants.NORMAL_STATUS);
		newPackage.setAppKey(new String[] { "mall" });
		when(sysTenantPackageService.getById(NEW_PACKAGE_ID)).thenReturn(newPackage);

		when(sysMenuService.list(any(Wrapper.class)))
			.thenReturn(List.of(menu("menu-keep"), menu("menu-added")));

		when(sysTenantMenuService.list()).thenReturn(List.of(
				tenantMenu("tm-keep", "menu-keep"), tenantMenu("tm-stale", "menu-stale")));

		when(sysRoleService.list()).thenReturn(List.of(
				role("role-admin", CommonConstants.ROLE_ADMIN_CODE), role("role-operator", "ROLE_OPERATOR")));
		when(sysRoleMenuService.list(any(Wrapper.class))).thenReturn(
				List.of(roleMenu("rm-a-keep", "role-admin", "menu-keep"),
						roleMenu("rm-a-stale", "role-admin", "menu-stale")),
				List.of(roleMenu("rm-o-keep", "role-operator", "menu-keep"),
						roleMenu("rm-o-stale", "role-operator", "menu-stale")));

		SysTenant patch = new SysTenant();
		patch.setId(TENANT_ID);
		patch.setPackageId(NEW_PACKAGE_ID);

		assertThat(service.updateTenant(patch)).isTrue();

		verify(sysTenantMenuService).removeByIds(List.of("tm-stale"));
		ArgumentCaptor<Collection<SysTenantMenu>> tenantMenus = collectionCaptor();
		verify(sysTenantMenuService).saveBatch(tenantMenus.capture());
		assertThat(tenantMenus.getValue()).extracting(SysTenantMenu::getMenuId).containsExactly("menu-added");

		verify(sysRoleMenuService, times(2)).remove(any(Wrapper.class));
		ArgumentCaptor<Collection<SysRoleMenu>> roleMenus = collectionCaptor();
		verify(sysRoleMenuService).saveBatch(roleMenus.capture());
		assertThat(roleMenus.getValue()).extracting(SysRoleMenu::getMenuId).containsExactly("menu-added");

		verify(tenantSessionRevoker).revokeTenant(eq(TENANT_ID), contains("套餐变更"));
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void renewExtendsFromCurrentAuthorizationEnd() {
		SysTenant existing = tenant(OLD_PACKAGE_ID, CommonConstants.NORMAL_STATUS);
		existing.setAuthEndTime(LocalDateTime.now().plusDays(10));
		when(sysTenantMapper.selectById(TENANT_ID)).thenReturn(existing);

		LocalDateTime newEnd = service.renew(TENANT_ID, 30);

		ArgumentCaptor<SysTenant> patch = ArgumentCaptor.forClass(SysTenant.class);
		verify(sysTenantMapper).updateById(patch.capture());
		assertThat(patch.getValue().getAuthEndTime()).isEqualTo(newEnd);
		assertThat(newEnd).isAfter(existing.getAuthEndTime());
	}

	@Test
	void disablingTenantRevokesSessions() {
		SysTenant existing = tenant(OLD_PACKAGE_ID, CommonConstants.NORMAL_STATUS);
		SysTenant disabled = tenant(OLD_PACKAGE_ID, "1");
		when(sysTenantMapper.selectById(TENANT_ID)).thenReturn(existing, disabled);

		assertThat(service.changeStatus(TENANT_ID, "1")).isTrue();

		verify(tenantSessionRevoker).revokeTenant(eq(TENANT_ID), contains("停用"));
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private static <T> ArgumentCaptor<Collection<T>> collectionCaptor() {
		return (ArgumentCaptor) ArgumentCaptor.forClass(Collection.class);
	}

	private static SysTenant tenant(String packageId, String status) {
		SysTenant tenant = new SysTenant();
		tenant.setId(TENANT_ID);
		tenant.setName("测试租户");
		tenant.setPackageId(packageId);
		tenant.setStatus(status);
		tenant.setAuthBeginTime(LocalDateTime.now().minusDays(1));
		tenant.setAuthEndTime(LocalDateTime.now().plusDays(30));
		return tenant;
	}

	private static SysMenu menu(String id) {
		SysMenu menu = new SysMenu();
		menu.setId(id);
		return menu;
	}

	private static SysTenantMenu tenantMenu(String id, String menuId) {
		SysTenantMenu tenantMenu = new SysTenantMenu();
		tenantMenu.setId(id);
		tenantMenu.setMenuId(menuId);
		return tenantMenu;
	}

	private static SysRole role(String id, String roleCode) {
		SysRole role = new SysRole();
		role.setId(id);
		role.setRoleCode(roleCode);
		return role;
	}

	private static SysRoleMenu roleMenu(String id, String roleId, String menuId) {
		SysRoleMenu roleMenu = new SysRoleMenu();
		roleMenu.setId(id);
		roleMenu.setRoleId(roleId);
		roleMenu.setMenuId(menuId);
		return roleMenu;
	}

}
