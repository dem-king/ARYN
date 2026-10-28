package com.aryn.cloud.message.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.dto.notice.NoticeSaveRequest;
import com.aryn.cloud.message.api.dto.notice.SystemNoticeSendDTO;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.notice.NoticeVO;
import com.aryn.cloud.message.service.NoticeService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 系统通知远程实现测试：租户由上下文决定，目标端必须是工作人员。
 */
class RemoteSystemNoticeServiceImplTest {

	private NoticeService noticeService;

	private RemoteSystemNoticeServiceImpl service;

	@BeforeEach
	void setUp() {
		noticeService = mock(NoticeService.class);
		service = new RemoteSystemNoticeServiceImpl(noticeService);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void sendsSystemNoticeToAllStaffOfCurrentTenant() {
		NoticeVO draft = new NoticeVO();
		draft.setId("notice-1");
		when(noticeService.createDraft(eq("tenant-1"), eq("SYSTEM"), eq("平台通知"), any())).thenReturn(draft);
		ArynTenantContextHolder.setTenantId("tenant-1");

		SystemNoticeSendDTO request = new SystemNoticeSendDTO();
		request.setTitle("租户授权即将到期");
		request.setSummary("授权即将到期");
		request.setContent("请及时联系平台续期");
		service.sendToAllStaff(request);

		ArgumentCaptor<NoticeSaveRequest> saveRequest = ArgumentCaptor.forClass(NoticeSaveRequest.class);
		verify(noticeService).createDraft(eq("tenant-1"), eq("SYSTEM"), eq("平台通知"), saveRequest.capture());
		assertThat(saveRequest.getValue().getTargetTypes()).containsExactly(MessageIdentityType.SYS_USER);
		assertThat(saveRequest.getValue().getCategory()).isEqualTo("system");
		verify(noticeService).publish("tenant-1", "SYSTEM", "notice-1");
	}

	@Test
	void rejectsWhenTenantContextMissing() {
		SystemNoticeSendDTO request = new SystemNoticeSendDTO();
		request.setTitle("标题");
		request.setContent("内容");

		assertThatThrownBy(() -> service.sendToAllStaff(request))
			.isInstanceOf(ArynBusinessException.class);
	}

}
