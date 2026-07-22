package com.aryn.cloud.user.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.dto.UserMessageAudienceRequest;
import com.aryn.cloud.user.api.vo.UserMessageRecipientVO;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteMessageAudienceServiceImplTest {

	@Mock
	private UserInfoMapper userInfoMapper;

	@AfterEach
	void clearTenant() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void returnsStableCursorAndTrimsLookAheadRecord() {
		ArynTenantContextHolder.setTenantId("tenant-1");
		UserMessageAudienceRequest request = request("tenant-1", 2);
		when(userInfoMapper.selectMessageRecipients(request, 3))
			.thenReturn(List.of(recipient("1"), recipient("2"), recipient("3")));

		var result = new RemoteMessageAudienceServiceImpl(userInfoMapper).queryRecipients(request);

		assertThat(result.getRecords()).extracting(UserMessageRecipientVO::getId).containsExactly("1", "2");
		assertThat(result.getNextCursor()).isEqualTo("2");
		assertThat(result.isHasMore()).isTrue();
	}

	@Test
	void clampsLargePagesAndRejectsCrossTenantRequest() {
		ArynTenantContextHolder.setTenantId("tenant-1");
		UserMessageAudienceRequest request = request("tenant-1", 5000);
		when(userInfoMapper.selectMessageRecipients(request, 1001)).thenReturn(List.of());

		new RemoteMessageAudienceServiceImpl(userInfoMapper).queryRecipients(request);
		verify(userInfoMapper).selectMessageRecipients(request, 1001);

		UserMessageAudienceRequest crossTenant = request("tenant-2", 10);
		assertThatThrownBy(() -> new RemoteMessageAudienceServiceImpl(userInfoMapper).queryRecipients(crossTenant))
			.isInstanceOf(ArynBusinessException.class);
	}

	@Test
	void rejectsMissingTenantBeforeQueryingMapper() {
		UserMessageAudienceRequest request = request("tenant-1", 10);
		assertThatThrownBy(() -> new RemoteMessageAudienceServiceImpl(userInfoMapper).queryRecipients(request))
			.isInstanceOf(ArynBusinessException.class);
		verifyNoInteractions(userInfoMapper);
	}

	private UserMessageAudienceRequest request(String tenantId, int limit) {
		UserMessageAudienceRequest request = new UserMessageAudienceRequest();
		request.setTenantId(tenantId);
		request.setLimit(limit);
		return request;
	}

	private UserMessageRecipientVO recipient(String id) {
		UserMessageRecipientVO recipient = new UserMessageRecipientVO();
		recipient.setId(id);
		return recipient;
	}

}
