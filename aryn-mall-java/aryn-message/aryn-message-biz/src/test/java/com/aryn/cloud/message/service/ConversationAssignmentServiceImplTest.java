package com.aryn.cloud.message.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.entity.MessageAgent;
import com.aryn.cloud.message.api.entity.MessageAssignmentLog;
import com.aryn.cloud.message.api.entity.MessageConversation;
import com.aryn.cloud.message.api.entity.MessageParticipant;
import com.aryn.cloud.message.api.enums.ConversationStatus;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.mapper.MessageAgentMapper;
import com.aryn.cloud.message.mapper.MessageAssignmentLogMapper;
import com.aryn.cloud.message.mapper.MessageConversationMapper;
import com.aryn.cloud.message.mapper.MessageParticipantMapper;
import com.aryn.cloud.message.service.impl.ConversationAssignmentServiceImpl;
import com.aryn.cloud.upms.api.remote.RemoteMessageStaffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationAssignmentServiceImplTest {

	private MessageConversationMapper conversationMapper;
	private MessageParticipantMapper participantMapper;
	private MessageAgentMapper agentMapper;
	private MessageAssignmentLogMapper logMapper;
	private AgentService agentService;
	private ChatMessageService chatMessageService;
	private RemoteMessageStaffService staffService;
	private ConversationAssignmentServiceImpl service;

	@BeforeEach
	void setUp() {
		conversationMapper = mock(MessageConversationMapper.class);
		participantMapper = mock(MessageParticipantMapper.class);
		agentMapper = mock(MessageAgentMapper.class);
		logMapper = mock(MessageAssignmentLogMapper.class);
		agentService = mock(AgentService.class);
		chatMessageService = mock(ChatMessageService.class);
		staffService = mock(RemoteMessageStaffService.class);
		TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
		when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
			TransactionCallback<?> callback = invocation.getArgument(0);
			return callback.doInTransaction(mock(TransactionStatus.class));
		});
		org.mockito.Mockito.doAnswer(invocation -> {
			Consumer<TransactionStatus> callback = invocation.getArgument(0);
			callback.accept(mock(TransactionStatus.class));
			return null;
		}).when(transactionTemplate).executeWithoutResult(any());
		service = new ConversationAssignmentServiceImpl(conversationMapper, participantMapper, agentMapper, logMapper,
				agentService, chatMessageService, transactionTemplate, staffService);
	}

	@Test
	void autoAssignmentUsesDatabaseCandidateOrder() {
		MessageAgent first = agent("staff-1", 1, LocalDateTime.now().minusHours(2));
		MessageAgent second = agent("staff-2", 1, LocalDateTime.now().minusHours(1));
		when(agentMapper.selectAssignmentCandidates("tenant-1", 200)).thenReturn(List.of(first, second));
		when(agentService.isAutoAssignable("tenant-1", "staff-1")).thenReturn(true);
		when(staffService.isCustomerServiceStaff("tenant-1", "staff-1")).thenReturn(true);
		when(agentMapper.incrementActiveCount("tenant-1", "staff-1")).thenReturn(1);
		when(conversationMapper.assignWaiting("tenant-1", "conversation-1", "staff-1")).thenReturn(1);
		when(conversationMapper.selectByIdForUpdate("tenant-1", "conversation-1")).thenReturn(conversation());

		String assignedStaffId = service.autoAssign("tenant-1", "conversation-1");

		assertEquals("staff-1", assignedStaffId);
		verify(agentService, never()).isAutoAssignable("tenant-1", "staff-2");
		verify(participantMapper).upsertActive(any(MessageParticipant.class));
		verify(logMapper).insert(any(MessageAssignmentLog.class));
	}

	@Test
	void noOnlineAgentLeavesConversationWaiting() {
		MessageAgent agent = agent("staff-1", 0, null);
		when(agentMapper.selectAssignmentCandidates("tenant-1", 200)).thenReturn(List.of(agent));
		when(agentService.isAutoAssignable("tenant-1", "staff-1")).thenReturn(false);

		assertNull(service.autoAssign("tenant-1", "conversation-1"));
		verify(conversationMapper, never()).assignWaiting(any(), any(), any());
	}

	@Test
	void concurrentClaimOnlyAcceptsSuccessfulConditionalUpdate() {
		when(staffService.isCustomerServiceStaff("tenant-1", "staff-1")).thenReturn(true);
		when(agentMapper.selectByStaffId("tenant-1", "staff-1")).thenReturn(agent("staff-1", 0, null));
		when(agentMapper.incrementActiveCount("tenant-1", "staff-1")).thenReturn(1);
		when(conversationMapper.assignWaiting("tenant-1", "conversation-1", "staff-1")).thenReturn(0);

		assertThrows(ArynBusinessException.class,
				() -> service.claim("tenant-1", "conversation-1", "staff-1", null));
	}

	@Test
	void closeReleasesLoadAndCreatesTwentyFourHourReopenWindow() {
		MessageConversation conversation = conversation();
		conversation.setAssignedStaffId("staff-1");
		conversation.setStatus(ConversationStatus.ACTIVE.name());
		when(conversationMapper.selectByIdForUpdate("tenant-1", "conversation-1")).thenReturn(conversation);

		service.close("tenant-1", "conversation-1", MessageIdentityType.SYS_USER, "staff-1", "已解决", false);

		verify(agentMapper).decrementActiveCount("tenant-1", "staff-1");
		assertEquals(ConversationStatus.CLOSED.name(), conversation.getStatus());
		assertTrue(Duration.between(conversation.getClosedTime(), conversation.getReopenDeadline()).toHours() == 24);
		verify(chatMessageService).sendSystem(org.mockito.ArgumentMatchers.eq("tenant-1"),
				org.mockito.ArgumentMatchers.eq("conversation-1"), any(), any(), any());
	}

	private MessageAgent agent(String staffId, int load, LocalDateTime lastAssignedTime) {
		MessageAgent agent = new MessageAgent();
		agent.setStaffId(staffId);
		agent.setEnabled("1");
		agent.setAutoAccept("1");
		agent.setCurrentActiveCount(load);
		agent.setMaxActiveCount(10);
		agent.setLastAssignedTime(lastAssignedTime);
		return agent;
	}

	private MessageConversation conversation() {
		MessageConversation conversation = new MessageConversation();
		conversation.setId("conversation-1");
		conversation.setTenantId("tenant-1");
		conversation.setCustomerId("member-1");
		conversation.setQueueCode("DEFAULT");
		conversation.setStatus(ConversationStatus.WAITING.name());
		conversation.setLastSeq(0L);
		return conversation;
	}

}
