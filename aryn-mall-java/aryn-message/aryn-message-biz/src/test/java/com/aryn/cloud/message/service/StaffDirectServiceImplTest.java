package com.aryn.cloud.message.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.entity.MessageConversation;
import com.aryn.cloud.message.api.entity.MessageParticipant;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;
import com.aryn.cloud.message.mapper.MessageConversationMapper;
import com.aryn.cloud.message.mapper.MessageParticipantMapper;
import com.aryn.cloud.message.service.impl.StaffDirectServiceImpl;
import com.aryn.cloud.upms.api.remote.RemoteMessageStaffService;
import com.aryn.cloud.upms.api.vo.StaffMessageAudiencePageVO;
import com.aryn.cloud.upms.api.vo.StaffMessageRecipientVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffDirectServiceImplTest {

	private MessageConversationMapper conversationMapper;
	private MessageParticipantMapper participantMapper;
	private RemoteMessageStaffService staffService;
	private StaffDirectServiceImpl service;

	@BeforeEach
	void setUp() {
		conversationMapper = mock(MessageConversationMapper.class);
		participantMapper = mock(MessageParticipantMapper.class);
		staffService = mock(RemoteMessageStaffService.class);
		service = new StaffDirectServiceImpl(conversationMapper, participantMapper, staffService);
	}

	@Test
	void staffPairUsesStableSortedUniqueKey() {
		when(staffService.queryRecipients(any())).thenReturn(targetPage("staff-1"));
		ConversationVO view = new ConversationVO();
		view.setId("conversation-1");
		when(conversationMapper.selectForParticipant(any(), any(), any(), any())).thenReturn(view);

		service.getOrCreate("tenant-1", "staff-9", "当前员工", null, "staff-1");

		ArgumentCaptor<MessageConversation> captor = ArgumentCaptor.forClass(MessageConversation.class);
		verify(conversationMapper).insert(captor.capture());
		assertEquals("staff-1:staff-9", captor.getValue().getStaffPairKey());
		verify(participantMapper, org.mockito.Mockito.times(2)).insertIgnore(any(MessageParticipant.class));
	}

	@Test
	void existingStaffConversationIsReused() {
		when(staffService.queryRecipients(any())).thenReturn(targetPage("staff-2"));
		MessageConversation existing = new MessageConversation();
		existing.setId("conversation-1");
		when(conversationMapper.selectActiveStaffConversation("tenant-1", "staff-1:staff-2")).thenReturn(existing);
		when(conversationMapper.selectForParticipant("tenant-1", "SYS_USER", "staff-1", "conversation-1"))
			.thenReturn(new ConversationVO());

		service.getOrCreate("tenant-1", "staff-1", "员工1", null, "staff-2");

		verify(conversationMapper, never()).insert(any(MessageConversation.class));
	}

	@Test
	void disabledOrCrossTenantTargetIsRejected() {
		StaffMessageAudiencePageVO empty = new StaffMessageAudiencePageVO();
		empty.setRecords(List.of());
		when(staffService.queryRecipients(any())).thenReturn(empty);

		assertThrows(ArynBusinessException.class,
				() -> service.getOrCreate("tenant-1", "staff-1", "员工1", null, "staff-2"));
	}

	@Test
	void selfConversationIsRejected() {
		assertThrows(ArynBusinessException.class,
				() -> service.getOrCreate("tenant-1", "staff-1", "员工1", null, "staff-1"));
	}

	private StaffMessageAudiencePageVO targetPage(String staffId) {
		StaffMessageRecipientVO target = new StaffMessageRecipientVO();
		target.setId(staffId);
		target.setNickname("目标员工");
		StaffMessageAudiencePageVO page = new StaffMessageAudiencePageVO();
		page.setRecords(List.of(target));
		return page;
	}

}
