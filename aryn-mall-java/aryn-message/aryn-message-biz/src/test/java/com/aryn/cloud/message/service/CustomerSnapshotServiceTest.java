package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.enums.ConversationType;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;
import com.aryn.cloud.message.mapper.MessageParticipantMapper;
import com.aryn.cloud.message.service.impl.CustomerSnapshotService;
import com.aryn.cloud.user.api.dto.UserMessageAudienceRequest;
import com.aryn.cloud.user.api.remote.RemoteMessageAudienceService;
import com.aryn.cloud.user.api.vo.UserMessageAudiencePageVO;
import com.aryn.cloud.user.api.vo.UserMessageRecipientVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 会员昵称/头像快照补齐（管理端显示「会员 + ID」问题的后端修复）。
 *
 * <p>修复前：会话参与者快照取自 TOC token，而 token 没有 nickname/avatar，
 * participant_name 为 NULL，管理端列表/会话头只能回退会员 ID。这里锁定：
 * 用户域资料优先、只补空字段、批量列表一次查询、查询失败不阻断会话主流程。</p>
 */
class CustomerSnapshotServiceTest {

	private MessageParticipantMapper participantMapper;

	private RemoteMessageAudienceService audienceService;

	private CustomerSnapshotService service;

	@BeforeEach
	void setUp() {
		participantMapper = mock(MessageParticipantMapper.class);
		audienceService = mock(RemoteMessageAudienceService.class);
		service = new CustomerSnapshotService(participantMapper, audienceService);
	}

	@Test
	void resolvesMemberProfileFromUserDomain() {
		when(audienceService.queryRecipients(any())).thenReturn(page(recipient("member-1", "八度的骄傲", "avatar-1")));

		UserMessageRecipientVO recipient = service.resolve("tenant-1", "member-1");

		assertEquals("八度的骄傲", recipient.getNickname());
		assertEquals("avatar-1", recipient.getAvatarUrl());
	}

	@Test
	void resolveReturnsNullWhenUserDomainFails() {
		when(audienceService.queryRecipients(any())).thenThrow(new IllegalStateException("rpc timeout"));

		assertNull(service.resolve("tenant-1", "member-1"));
	}

	@Test
	void fillMissingWritesProfileIntoBlankConversationSnapshot() {
		ConversationVO view = conversation("conversation-1", "member-1", null, null);
		when(audienceService.queryRecipients(any())).thenReturn(page(recipient("member-1", "八度的骄傲", "avatar-1")));

		service.fillMissing("tenant-1", "conversation-1", "member-1", "token昵称", "token头像", view);

		assertEquals("八度的骄傲", view.getCustomerName());
		assertEquals("avatar-1", view.getCustomerAvatar());
		verify(participantMapper).fillSnapshotIfBlank("tenant-1", "conversation-1", "MALL_USER", "member-1",
				"八度的骄傲", "avatar-1");
	}

	@Test
	void fillMissingKeepsExistingNameAndFallsBackWhenUserDomainHasNoData() {
		ConversationVO view = conversation("conversation-1", "member-1", "已有昵称", null);
		when(audienceService.queryRecipients(any())).thenReturn(page(recipient("member-1", null, null)));

		service.fillMissing("tenant-1", "conversation-1", "member-1", "token昵称", "token头像", view);

		assertEquals("已有昵称", view.getCustomerName());
		assertEquals("token头像", view.getCustomerAvatar());
	}

	@Test
	void completeSnapshotDoesNotCallUserDomainOrDatabase() {
		ConversationVO view = conversation("conversation-1", "member-1", "已有昵称", "已有头像");

		service.fillMissing("tenant-1", "conversation-1", "member-1", null, null, view);

		verify(audienceService, never()).queryRecipients(any());
		verify(participantMapper, never()).fillSnapshotIfBlank(anyString(), anyString(), anyString(), anyString(),
				any(), any());
	}

	@Test
	void fillMissingBatchResolvesOnceAndBackfillsEveryIncompleteRow() {
		ConversationVO first = conversation("conversation-1", "member-1", null, null);
		ConversationVO second = conversation("conversation-2", "member-2", null, null);
		ConversationVO direct = new ConversationVO();
		direct.setId("direct-1");
		direct.setConversationType(ConversationType.STAFF_DIRECT.name());
		when(audienceService.queryRecipients(any())).thenReturn(page(
				recipient("member-1", "会员一", "avatar-1"), recipient("member-2", "会员二", "avatar-2")));

		service.fillMissingBatch("tenant-1", List.of(first, second, direct));

		ArgumentCaptor<UserMessageAudienceRequest> request = ArgumentCaptor.forClass(UserMessageAudienceRequest.class);
		verify(audienceService).queryRecipients(request.capture());
		assertEquals(List.of("member-1", "member-2"), request.getValue().getUserIds());
		assertEquals("会员一", first.getCustomerName());
		assertEquals("avatar-1", first.getCustomerAvatar());
		assertEquals("会员二", second.getCustomerName());
		assertEquals("avatar-2", second.getCustomerAvatar());
		verify(participantMapper).fillSnapshotIfBlank(eq("tenant-1"), eq("conversation-1"), eq("MALL_USER"),
				eq("member-1"), eq("会员一"), eq("avatar-1"));
		verify(participantMapper).fillSnapshotIfBlank(eq("tenant-1"), eq("conversation-2"), eq("MALL_USER"),
				eq("member-2"), eq("会员二"), eq("avatar-2"));
	}

	@Test
	void fillMissingBatchKeepsExistingNames() {
		ConversationVO view = conversation("conversation-1", "member-1", "已有昵称", null);
		when(audienceService.queryRecipients(any())).thenReturn(page(recipient("member-1", "用户域昵称", "avatar-1")));

		service.fillMissingBatch("tenant-1", List.of(view));

		assertEquals("已有昵称", view.getCustomerName());
		assertEquals("avatar-1", view.getCustomerAvatar());
	}

	private UserMessageAudiencePageVO page(UserMessageRecipientVO... recipients) {
		UserMessageAudiencePageVO page = new UserMessageAudiencePageVO();
		page.setRecords(List.of(recipients));
		return page;
	}

	private UserMessageRecipientVO recipient(String id, String nickname, String avatarUrl) {
		UserMessageRecipientVO recipient = new UserMessageRecipientVO();
		recipient.setId(id);
		recipient.setNickname(nickname);
		recipient.setAvatarUrl(avatarUrl);
		return recipient;
	}

	private ConversationVO conversation(String id, String customerId, String name, String avatar) {
		ConversationVO view = new ConversationVO();
		view.setId(id);
		view.setCustomerId(customerId);
		view.setConversationType(ConversationType.CUSTOMER_SERVICE.name());
		view.setCustomerName(name);
		view.setCustomerAvatar(avatar);
		return view;
	}

}
