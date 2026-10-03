package com.aryn.cloud.message.service;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.entity.MessageAgent;
import com.aryn.cloud.message.api.entity.MessageConversation;
import com.aryn.cloud.message.api.entity.MessageParticipant;
import com.aryn.cloud.message.api.enums.ConversationStatus;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ConversationAttentionVO;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;
import com.aryn.cloud.message.mapper.MessageAgentMapper;
import com.aryn.cloud.message.mapper.MessageConversationMapper;
import com.aryn.cloud.message.mapper.MessageParticipantMapper;
import com.aryn.cloud.message.service.impl.ConversationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationServiceImplTest {

	private MessageConversationMapper conversationMapper;
	private MessageParticipantMapper participantMapper;
	private MessageAgentMapper agentMapper;
	private ConversationServiceImpl service;

	@BeforeEach
	void setUp() {
		conversationMapper = mock(MessageConversationMapper.class);
		participantMapper = mock(MessageParticipantMapper.class);
		agentMapper = mock(MessageAgentMapper.class);
		service = new ConversationServiceImpl(conversationMapper, participantMapper, agentMapper, new ObjectMapper(),
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

	@Test
	void attentionIncludesWaitingPoolOnlyForEnabledAgent() {
		MessageAgent enabled = new MessageAgent();
		enabled.setEnabled(CommonConstants.YES);
		when(agentMapper.selectByStaffId("tenant-1", "staff-1")).thenReturn(enabled);
		when(conversationMapper.selectUnreadForParticipant("tenant-1", "SYS_USER", "staff-1", 10))
			.thenReturn(new ArrayList<>());
		when(conversationMapper.countUnreadForParticipant("tenant-1", "SYS_USER", "staff-1")).thenReturn(2L);
		when(conversationMapper.countWaiting("tenant-1", "DEFAULT")).thenReturn(3L);
		MessageConversation waiting = conversation(0L);
		waiting.setId("conversation-waiting");
		when(conversationMapper.selectWaiting("tenant-1", "DEFAULT", 10)).thenReturn(List.of(waiting));

		ConversationAttentionVO attention = service.attention("tenant-1", MessageIdentityType.SYS_USER, "staff-1",
				"DEFAULT", 10);

		assertEquals(2L, attention.getUnreadConversations());
		assertEquals(3L, attention.getWaitingTotal());
		assertEquals(List.of("conversation-waiting"),
				attention.getConversations().stream().map(ConversationVO::getId).toList());
	}

	@Test
	void attentionSkipsWaitingPoolForNonAgent() {
		when(agentMapper.selectByStaffId("tenant-1", "staff-2")).thenReturn(null);
		when(conversationMapper.selectUnreadForParticipant("tenant-1", "SYS_USER", "staff-2", 10))
			.thenReturn(new ArrayList<>());

		ConversationAttentionVO attention = service.attention("tenant-1", MessageIdentityType.SYS_USER, "staff-2",
				"DEFAULT", 10);

		assertEquals(0L, attention.getWaitingTotal());
		verify(conversationMapper, never()).countWaiting(any(), any());
		verify(conversationMapper, never()).selectWaiting(any(), any(), anyInt());
	}

	@Test
	void attentionDoesNotDuplicateConversationsAlreadyUnread() {
		MessageAgent enabled = new MessageAgent();
		enabled.setEnabled(CommonConstants.YES);
		when(agentMapper.selectByStaffId("tenant-1", "staff-1")).thenReturn(enabled);
		ConversationVO unread = new ConversationVO();
		unread.setId("conversation-1");
		when(conversationMapper.selectUnreadForParticipant("tenant-1", "SYS_USER", "staff-1", 10))
			.thenReturn(new ArrayList<>(List.of(unread)));
		when(conversationMapper.selectWaiting("tenant-1", "DEFAULT", 10)).thenReturn(List.of(conversation(3L)));

		ConversationAttentionVO attention = service.attention("tenant-1", MessageIdentityType.SYS_USER, "staff-1",
				"DEFAULT", 10);

		assertEquals(List.of("conversation-1"),
				attention.getConversations().stream().map(ConversationVO::getId).toList());
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
