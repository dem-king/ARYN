package com.aryn.cloud.vessel.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import com.aryn.cloud.vessel.service.VesselService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 靠港状态流转任务的租户遍历契约测试：逐租户注入上下文、单租户失败不阻断、收尾清理。
 */
class VesselCallStatusJobHandlerTest {

	private VesselService vesselService;

	private RemoteTenantService remoteTenantService;

	private VesselCallStatusJobHandler handler;

	@BeforeEach
	void setUp() {
		vesselService = mock(VesselService.class);
		remoteTenantService = mock(RemoteTenantService.class);
		handler = new VesselCallStatusJobHandler(vesselService, remoteTenantService);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	private SysTenant tenant(String id) {
		SysTenant sysTenant = new SysTenant();
		sysTenant.setId(id);
		return sysTenant;
	}

	@Test
	@DisplayName("逐租户推进并在调用时注入对应租户上下文，收尾清空")
	void iteratesTenantsWithTenantContext() {
		when(remoteTenantService.list()).thenReturn(Arrays.asList(tenant("tenant-1"), tenant("tenant-2")));
		List<String> seenTenants = new ArrayList<>();
		when(vesselService.refreshCallStatus(anyString())).thenAnswer(invocation -> {
			seenTenants.add(ArynTenantContextHolder.getTenantId());
			return 1;
		});

		handler.vesselCallStatusJobHandler();

		assertEquals(Arrays.asList("tenant-1", "tenant-2"), seenTenants);
		assertNull(ArynTenantContextHolder.getTenantId());
	}

	@Test
	@DisplayName("单租户执行失败不阻断其余租户")
	void tenantFailureDoesNotBlockOthers() {
		when(remoteTenantService.list()).thenReturn(Arrays.asList(tenant("tenant-bad"), tenant("tenant-good")));
		doThrow(new RuntimeException("db down")).when(vesselService).refreshCallStatus("tenant-bad");
		when(vesselService.refreshCallStatus("tenant-good")).thenReturn(2);

		handler.vesselCallStatusJobHandler();

		verify(vesselService).refreshCallStatus("tenant-good");
		assertNull(ArynTenantContextHolder.getTenantId());
	}

	@Test
	@DisplayName("无租户时直接返回，不触碰服务")
	void returnsQuietlyWhenNoTenant() {
		when(remoteTenantService.list()).thenReturn(Collections.emptyList());

		handler.vesselCallStatusJobHandler();

		verify(vesselService, never()).refreshCallStatus(anyString());
		assertNull(ArynTenantContextHolder.getTenantId());
	}

}
