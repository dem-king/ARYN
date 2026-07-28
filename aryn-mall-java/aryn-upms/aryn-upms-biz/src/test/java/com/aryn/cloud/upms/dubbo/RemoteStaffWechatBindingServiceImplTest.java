package com.aryn.cloud.upms.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.entity.SysUserWechatBinding;
import com.aryn.cloud.upms.mapper.SysUserWechatBindingMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RemoteStaffWechatBindingServiceImplTest {

	@AfterEach
	void clearTenant() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void bindsCurrentTenantAndReadsBoundOpenId() {
		ArynTenantContextHolder.setTenantId("tenant-1");
		SysUserWechatBindingMapper mapper = mock(SysUserWechatBindingMapper.class);
		when(mapper.upsertBinding(any())).thenReturn(1);
		when(mapper.selectBoundOpenId("tenant-1", "staff-1", "delivery-app")).thenReturn("openid-1");
		RemoteStaffWechatBindingServiceImpl service = new RemoteStaffWechatBindingServiceImpl(mapper);

		SysUserWechatBinding binding = service.bind("tenant-1", "staff-1", "delivery-app", "openid-1");

		assertThat(binding.getTenantId()).isEqualTo("tenant-1");
		assertThat(binding.getStatus()).isEqualTo("BOUND");
		assertThat(service.getBoundOpenId("tenant-1", "staff-1", "delivery-app")).isEqualTo("openid-1");
		verify(mapper).upsertBinding(any(SysUserWechatBinding.class));
	}

	@Test
	void rejectsCrossTenantBinding() {
		ArynTenantContextHolder.setTenantId("tenant-1");
		RemoteStaffWechatBindingServiceImpl service =
			new RemoteStaffWechatBindingServiceImpl(mock(SysUserWechatBindingMapper.class));

		assertThatThrownBy(() -> service.bind("tenant-2", "staff-1", "delivery-app", "openid-1"))
			.isInstanceOf(ArynBusinessException.class);
	}
}
