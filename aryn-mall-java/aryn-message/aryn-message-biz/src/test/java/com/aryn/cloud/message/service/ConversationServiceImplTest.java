package com.aryn.cloud.message.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.entity.MessageConversation;
import com.aryn.cloud.message.api.entity.MessageParticipant;
import com.aryn.cloud.message.api.enums.ConversationStatus;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;
import com.aryn.cloud.message.mapper.MessageConversationMapper;
import com.aryn.cloud.message.mapper.MessageParticipantMapper;
import com.aryn.cloud.message.service.impl.ConversationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationServiceImplTest {

	private MessageConversationMapper conversationMapper;
	private MessageParticipantMapper participantMapper;
	private ConversationServiceImpl service;

	@BeforeEach
	void setUp() {
		conversationMapper = mock(MessageConversationMapper.class);
		participantMapper = mock(MessageParticipantMapper.class);
		service = new ConversationServiceImpl(conversationMapper, participantMapper, new ObjectMapper(),
				mock(ConversationAssignmentService.class), mock(com.aryn.cloud.user.api.remote.RemoteMessageAudienceService.class));
	}

	@Test
	void existingActiveCustomerConversationIsReused() {
		MessageConversation existing = conversation(5L);
		ConversationVO view = new ConversationVO();
		view.setId("conversation-1");
		when(conversationMapper.selectActiveCustomerConversation("tenant-1", "member-1", "DEFAULT"))
			.thenReturn(existing);
		when(conversationMapper.selectForParticipant("tenant-1", "MALL_USER", "member-1", "conversation-1"))
			.thenReturn(view);

		service.getOrCreateCustomerService("tenant-1", "member-1", "会员", null, null, null);

		verify(conversationMapper, never()).insert(any(MessageConversation.class));
		verify(participantMapper, never()).insertIgnore(any(MessageParticipant.class));
	}

	@Test
	void readCursorNeverDecreases() {
		MessageConversation conversation = conversation(10L);
		MessageParticipant participant = participant(8L);
		when(conversationMapper.selectByIdForUpdate("tenant-1", "conversation-1")).thenReturn(conversation);
		when(participantMapper.selectActiveParticipant("tenant-1", "conversation-1", "MALL_USER", "member-1"))
			.thenReturn(participant);

		service.markRead("tenant-1", MessageIdentityType.MALL_USER, "member-1", "conversation-1", 6L);

		verify(participantMapper, never()).updateLastReadSeq(any(), any(), any(), any(),
				org.mockito.ArgumentMatchers.anyLong());
	}

	@Test
	void readCursorCannotExceedConversationSequence() {
		MessageConversation conversation = conversation(10L);
		MessageParticipant participant = participant(8L);
		when(conversationMapper.selectByIdForUpdate("tenant-1", "conversation-1")).thenReturn(conversation);
		when(participantMapper.selectActiveParticipant("tenant-1", "conversation-1", "MALL_USER", "member-1"))
			.thenReturn(participant);

		assertThrows(ArynBusinessException.class,
				() -> service.markRead("tenant-1", MessageIdentityType.MALL_USER, "member-1", "conversation-1", 11L));
	}

	@Test
	void participantIdentityIsRequiredForDetails() {
		when(conversationMapper.selectForParticipant("tenant-1", "SYS_USER", "staff-2", "conversation-1"))
			.thenReturn(null);

		assertThrows(ArynBusinessException.class,
				() -> service.get("tenant-1", MessageIdentityType.SYS_USER, "staff-2", "conversation-1"));
	}

	private MessageConversation conversation(long lastSeq) {
		MessageConversation conversation = new MessageConversation();
		conversation.setId("conversation-1");
		conversation.setTenantId("tenant-1");
		conversation.setStatus(ConversationStatus.WAITING.name());
		conversation.setLastSeq(lastSeq);
		return conversation;
	}

	private MessageParticipant participant(long lastReadSeq) {
		MessageParticipant participant = new MessageParticipant();
		participant.setLastReadSeq(lastReadSeq);
		return participant;
	}

}
