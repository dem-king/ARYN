package com.aryn.cloud.message.controller.app;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.message.api.dto.conversation.CustomerServiceConversationRequest;
import com.aryn.cloud.message.api.enums.ConversationStatus;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;
import com.aryn.cloud.message.service.ChatMessageService;
import com.aryn.cloud.message.service.ConversationAssignmentService;
import com.aryn.cloud.message.service.ConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C 端客服入口测试：已存在 WAITING 会话的自动分配重试必须在建会话事务提交后执行。
 */
class AppConversationControllerTest {

	private ConversationService conversationService;
	private ConversationAssignmentService assignmentService;
	private AppConversationController controller;

	@BeforeEach
	void setUp() {
		conversationService = mock(ConversationService.class);
		assignmentService = mock(ConversationAssignmentService.class);
		controller = new AppConversationController(conversationService, mock(ChatMessageService.class),
				assignmentService);
	}

	@Test
	void reenteringWaitingConversationRetriesAutoAssignAfterTransactionCommits() {
		ConversationVO waiting = new ConversationVO();
		waiting.setId("conversation-1");
		waiting.setStatus(ConversationStatus.WAITING.name());
		ConversationVO assigned = new ConversationVO();
		assigned.setId("conversation-1");
		assigned.setStatus(ConversationStatus.ASSIGNED.name());
		assigned.setAssignedStaffId("staff-1");
		when(conversationService.getOrCreateCustomerService(anyString(), anyString(), any(), any(), any(), any()))
			.thenReturn(waiting);
		when(conversationService.get(eq("tenant-1"), eq(MessageIdentityType.MALL_USER), eq("member-1"),
				eq("conversation-1")))
			.thenReturn(assigned);

		ArynUser user = user();
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(() -> SecurityUtils.requireUser(DeviceTypeEnum.TOC)).thenReturn(user);

			ConversationVO result = controller.customerService(new CustomerServiceConversationRequest()).getData();

			verify(assignmentService).autoAssign("tenant-1", "conversation-1");
			assertEquals("staff-1", result.getAssignedStaffId());
			assertEquals(ConversationStatus.ASSIGNED.name(), result.getStatus());
		}
	}

	@Test
	void activeConversationSkipsAutoAssignRetry() {
		ConversationVO active = new ConversationVO();
		active.setId("conversation-1");
		active.setStatus(ConversationStatus.ACTIVE.name());
		active.setAssignedStaffId("staff-1");
		when(conversationService.getOrCreateCustomerService(anyString(), anyString(), any(), any(), any(), any()))
			.thenReturn(active);

		ArynUser user = user();
		try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class)) {
			security.when(() -> SecurityUtils.requireUser(DeviceTypeEnum.TOC)).thenReturn(user);

			ConversationVO result = controller.customerService(new CustomerServiceConversationRequest()).getData();

			verify(assignmentService, never()).autoAssign(any(), any());
			verify(conversationService, never()).get(any(), any(), any(), any());
			assertEquals("staff-1", result.getAssignedStaffId());
		}
	}

	private ArynUser user() {
		ArynUser user = new ArynUser();
		user.setTenantId("tenant-1");
		user.setUserId("member-1");
		user.setNickname("会员");
		return user;
	}

}
