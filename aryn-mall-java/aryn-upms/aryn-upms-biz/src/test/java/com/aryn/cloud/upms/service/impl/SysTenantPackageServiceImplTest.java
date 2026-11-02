package com.aryn.cloud.upms.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.entity.SysTenantPackage;
import com.aryn.cloud.upms.mapper.SysTenantPackageMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 租户套餐应用范围契约。
 *
 * <p>套餐里的 applicationKey 会在建租户时整包展开为租户菜单与管理员角色授权，
 * 一旦写入平台应用（菜单管理、字典管理、代码生成等），租户管理员就会拿到平台级能力。
 * 因此写套餐必须在落库前拒绝，而不是依赖前端下拉过滤。
 */
class SysTenantPackageServiceImplTest {

	private SysTenantPackageServiceImpl service;

	@BeforeEach
	void setUp() {
		SysTenantPackageMapper sysTenantPackageMapper = mock(SysTenantPackageMapper.class);
		service = new SysTenantPackageServiceImpl();
		ReflectionTestUtils.setField(service, "baseMapper", sysTenantPackageMapper);
		when(sysTenantPackageMapper.insert(any(SysTenantPackage.class))).thenReturn(1);
		when(sysTenantPackageMapper.updateById(any(SysTenantPackage.class))).thenReturn(1);
	}

	@Test
	void saveRejectsPlatformAppKey() {
		SysTenantPackage tenantPackage = tenantPackage("app_base", CommonConstants.PLATFORM_APP_KEY);

		assertThatThrownBy(() -> service.save(tenantPackage))
			.isInstanceOfSatisfying(ArynBusinessException.class, exception -> assertThat(exception.getMsg()).contains("平台应用不可分配给租户套餐"));
	}

	@Test
	void updateByIdRejectsPlatformAppKey() {
		SysTenantPackage tenantPackage = tenantPackage(CommonConstants.PLATFORM_APP_KEY);

		assertThatThrownBy(() -> service.updateById(tenantPackage))
			.isInstanceOfSatisfying(ArynBusinessException.class, exception -> assertThat(exception.getMsg()).contains("平台应用不可分配给租户套餐"));
	}

	@Test
	void saveAllowsAssignableAppKeys() {
		SysTenantPackage tenantPackage = tenantPackage("app_base", "app_market");

		assertThat(service.save(tenantPackage)).isTrue();
	}

	@Test
	void saveAllowsPackageWithoutAppKeys() {
		SysTenantPackage tenantPackage = new SysTenantPackage();
		tenantPackage.setName("空套餐");

		assertThat(service.save(tenantPackage)).isTrue();
	}

	private static SysTenantPackage tenantPackage(String... appKeys) {
		SysTenantPackage tenantPackage = new SysTenantPackage();
		tenantPackage.setId("pkg-1");
		tenantPackage.setName("基础套餐");
		tenantPackage.setAppKey(appKeys);
		return tenantPackage;
	}

}
