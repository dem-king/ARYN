package com.aryn.cloud.upms.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.dto.StaffMessageAudienceRequest;
import com.aryn.cloud.upms.api.vo.StaffMessageRecipientVO;
import com.aryn.cloud.upms.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteMessageStaffServiceImplTest {

	@Mock
	private SysUserMapper sysUserMapper;

	@AfterEach
	void clearTenant() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void returnsStableCursorAndChecksCustomerServiceQualification() {
		ArynTenantContextHolder.setTenantId("tenant-1");
		StaffMessageAudienceRequest request = new StaffMessageAudienceRequest();
		request.setTenantId("tenant-1");
		request.setLimit(1);
		when(sysUserMapper.selectMessageRecipients(request, 2))
			.thenReturn(List.of(recipient("10"), recipient("11")));
		when(sysUserMapper.countCustomerServiceStaff("tenant-1", "10")).thenReturn(1);

		RemoteMessageStaffServiceImpl service = new RemoteMessageStaffServiceImpl(sysUserMapper);
		var page = service.queryRecipients(request);

		assertThat(page.getRecords()).extracting(StaffMessageRecipientVO::getId).containsExactly("10");
		assertThat(page.getNextCursor()).isEqualTo("10");
		assertThat(page.isHasMore()).isTrue();
		assertThat(service.isCustomerServiceStaff("tenant-1", "10")).isTrue();
	}

	@Test
	void rejectsCrossTenantQualificationCheck() {
		ArynTenantContextHolder.setTenantId("tenant-1");
		assertThatThrownBy(() -> new RemoteMessageStaffServiceImpl(sysUserMapper)
			.isCustomerServiceStaff("tenant-2", "10")).isInstanceOf(ArynBusinessException.class);
	}

	private StaffMessageRecipientVO recipient(String id) {
		StaffMessageRecipientVO recipient = new StaffMessageRecipientVO();
		recipient.setId(id);
		return recipient;
	}

}
