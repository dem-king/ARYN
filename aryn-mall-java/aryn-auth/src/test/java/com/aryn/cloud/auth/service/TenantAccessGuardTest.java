package com.aryn.cloud.auth.service;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.dto.TenantAccessDTO;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TenantAccessGuardTest {

	private final RemoteTenantService remoteTenantService = mock(RemoteTenantService.class);

	private final TenantAccessGuard guard = new TenantAccessGuard(remoteTenantService);

	@Test
	void rejectsMissingTenant() {
		when(remoteTenantService.getAccess("tenant-a")).thenReturn(null);

		assertThatThrownBy(() -> guard.assertAvailable("tenant-a"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.asString().contains("不存在");
	}

	@Test
	void rejectsDisabledTenant() {
		TenantAccessDTO access = access("1", null, null);
		when(remoteTenantService.getAccess("tenant-a")).thenReturn(access);

		assertThatThrownBy(() -> guard.assertAvailable("tenant-a"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.asString().contains("停用");
	}

	@Test
	void rejectsTenantBeforeAuthBeginTime() {
		TenantAccessDTO access = access(CommonConstants.NORMAL_STATUS, LocalDateTime.now().plusDays(1), null);
		when(remoteTenantService.getAccess("tenant-a")).thenReturn(access);

		assertThatThrownBy(() -> guard.assertAvailable("tenant-a"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.asString().contains("尚未生效");
	}

	@Test
	void rejectsExpiredTenant() {
		TenantAccessDTO access = access(CommonConstants.NORMAL_STATUS, null, LocalDateTime.now().minusMinutes(1));
		when(remoteTenantService.getAccess("tenant-a")).thenReturn(access);

		assertThatThrownBy(() -> guard.assertAvailable("tenant-a"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.asString().contains("到期");
	}

	@Test
	void allowsActiveTenant() {
		TenantAccessDTO access = access(CommonConstants.NORMAL_STATUS, LocalDateTime.now().minusDays(1),
				LocalDateTime.now().plusDays(1));
		when(remoteTenantService.getAccess("tenant-a")).thenReturn(access);

		assertThatCode(() -> guard.assertAvailable("tenant-a")).doesNotThrowAnyException();
	}

	@Test
	void alwaysAllowsPlatformTenantWithoutRemoteLookup() {
		assertThatCode(() -> guard.assertAvailable(CommonConstants.PLATFORM_TENANT_ID))
			.doesNotThrowAnyException();

		verifyNoInteractions(remoteTenantService);
	}

	private static TenantAccessDTO access(String status, LocalDateTime begin, LocalDateTime end) {
		TenantAccessDTO access = new TenantAccessDTO();
		access.setTenantId("tenant-a");
		access.setStatus(status);
		access.setAuthBeginTime(begin);
		access.setAuthEndTime(end);
		return access;
	}

}
