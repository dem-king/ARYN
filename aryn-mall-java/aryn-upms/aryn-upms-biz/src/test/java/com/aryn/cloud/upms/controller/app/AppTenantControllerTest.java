package com.aryn.cloud.upms.controller.app;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.vo.SysTenantShopVO;
import com.aryn.cloud.upms.controller.SysTenantController;
import com.aryn.cloud.upms.service.ISysTenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppTenantControllerTest {

	@AfterEach
	void clearTenantContext() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void returnsOnlyCurrentTenantShopWhitelist() {
		ISysTenantService tenantService = mock(ISysTenantService.class);
		SysTenant tenant = new SysTenant();
		tenant.setId("tenant-a");
		tenant.setName("A shop");
		tenant.setLogoUrl("logo.png");
		tenant.setAddress("Shanghai");
		tenant.setSiteUrl("https://shop.example.com");
		tenant.setPhone("10086");
		tenant.setEmail("private@example.com");
		tenant.setPackageId("private-package");
		tenant.setAuthEndTime(LocalDateTime.now());
		when(tenantService.getById("tenant-a")).thenReturn(tenant);
		ArynTenantContextHolder.setTenantId("tenant-a");

		Result<SysTenantShopVO> result = new AppTenantController(tenantService).getShopInfo();

		assertEquals(Set.of("address", "id", "logoUrl", "name", "phone", "siteUrl"),
				Arrays.stream(SysTenantShopVO.class.getDeclaredFields()).map(Field::getName).collect(Collectors.toSet()));
		assertEquals("tenant-a", result.getData().getId());
		assertEquals("A shop", result.getData().getName());
		assertEquals("10086", result.getData().getPhone());
		verify(tenantService).getById("tenant-a");
	}

	@Test
	void legacyTenantListRequiresTenantManagementPermission() throws NoSuchMethodException {
		SaCheckPermission permission = SysTenantController.class.getDeclaredMethod("getList")
			.getAnnotation(SaCheckPermission.class);

		assertNotNull(permission);
		assertEquals(Set.of("upms:systenant:page"), Set.of(permission.value()));
	}

	@Test
	void currentShopEndpointIsPublicInBootAndCloud() throws IOException {
		Path javaRoot = Path.of("").toAbsolutePath();
		while (javaRoot != null && !Files.exists(javaRoot.resolve("aryn-boot"))) {
			javaRoot = javaRoot.getParent();
		}
		assertNotNull(javaRoot, "Cannot locate aryn-mall-java");
		String bootConfig = Files.readString(javaRoot.resolve("aryn-boot/src/main/resources/application.yml"));
		String cloudConfig = Files.readString(javaRoot.resolve("db/cloud/3aryn_nacos.sql"));

		assertTrue(bootConfig.contains("- /app/tenant/shop-info"));
		assertTrue(cloudConfig.contains("- /upms/app/tenant/shop-info"));
	}

}
