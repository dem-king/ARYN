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
import com.aryn.cloud.upms.api.vo.StaffMessageAudiencePageVO;
import com.aryn.cloud.upms.api.vo.StaffMessageRecipientVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
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
	void newAssigneeStartsWithUnreadHistory() {
		MessageConversation conversation = conversation();
		conversation.setLastSeq(9L);
		when(agentMapper.selectAssignmentCandidates("tenant-1", 200))
			.thenReturn(List.of(agent("staff-1", 0, null)));
		when(agentService.isAutoAssignable("tenant-1", "staff-1")).thenReturn(true);
		when(staffService.isCustomerServiceStaff("tenant-1", "staff-1")).thenReturn(true);
		when(agentMapper.incrementActiveCount("tenant-1", "staff-1")).thenReturn(1);
		when(conversationMapper.assignWaiting("tenant-1", "conversation-1", "staff-1")).thenReturn(1);
		when(conversationMapper.selectByIdForUpdate("tenant-1", "conversation-1")).thenReturn(conversation);

		service.autoAssign("tenant-1", "conversation-1");

		// 分配后新坐席的历史消息应计为未读：last_read_seq 必须从 0 开始，而不是当前 last_seq。
		ArgumentCaptor<MessageParticipant> captor = ArgumentCaptor.forClass(MessageParticipant.class);
		verify(participantMapper).upsertActive(captor.capture());
		assertEquals(0L, captor.getValue().getLastReadSeq().longValue());
	}

	@Test
	void assigneeParticipantCarriesStaffNicknameSnapshot() {
		MessageConversation conversation = conversation();
		conversation.setLastSeq(4L);
		when(agentMapper.selectAssignmentCandidates("tenant-1", 200))
			.thenReturn(List.of(agent("staff-1", 0, null)));
		when(agentService.isAutoAssignable("tenant-1", "staff-1")).thenReturn(true);
		when(staffService.isCustomerServiceStaff("tenant-1", "staff-1")).thenReturn(true);
		when(agentMapper.incrementActiveCount("tenant-1", "staff-1")).thenReturn(1);
		when(conversationMapper.assignWaiting("tenant-1", "conversation-1", "staff-1")).thenReturn(1);
		when(conversationMapper.selectByIdForUpdate("tenant-1", "conversation-1")).thenReturn(conversation);
		StaffMessageRecipientVO staff = new StaffMessageRecipientVO();
		staff.setId("staff-1");
		staff.setNickname("客服一号");
		staff.setAvatar("https://cdn.example.com/avatar.png");
		StaffMessageAudiencePageVO page = new StaffMessageAudiencePageVO();
		page.setRecords(List.of(staff));
		when(staffService.queryRecipients(any())).thenReturn(page);

		service.autoAssign("tenant-1", "conversation-1");

		// 会话视图的 assignedStaffName/Avatar 来自参与者快照，分配时必须写入昵称/头像。
		ArgumentCaptor<MessageParticipant> captor = ArgumentCaptor.forClass(MessageParticipant.class);
		verify(participantMapper).upsertActive(captor.capture());
		assertEquals("客服一号", captor.getValue().getParticipantName());
		assertEquals("https://cdn.example.com/avatar.png", captor.getValue().getParticipantAvatar());
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
	void waitingRequiresEnabledAgentAndReturnsCustomerSnapshot() {
		com.aryn.cloud.message.api.vo.conversation.ConversationVO waiting = new com.aryn.cloud.message.api.vo.conversation.ConversationVO();
		waiting.setId("conversation-1");
		waiting.setCustomerName("会员昵称");
		when(agentService.isEnabledAgent("tenant-1", "staff-1")).thenReturn(true);
		when(conversationMapper.selectWaitingPool("tenant-1", "DEFAULT", 50)).thenReturn(List.of(waiting));

		List<com.aryn.cloud.message.api.vo.conversation.ConversationVO> result = service.waiting("tenant-1", "staff-1",
				null, 50);

		assertEquals(1, result.size());
		assertEquals("会员昵称", result.get(0).getCustomerName());
	}

	@Test
	void waitingRejectsNonAgentCaller() {
		when(agentService.isEnabledAgent("tenant-1", "staff-2")).thenReturn(false);

		assertThrows(ArynBusinessException.class, () -> service.waiting("tenant-1", "staff-2", "DEFAULT", 10));
		verify(conversationMapper, never()).selectWaitingPool(any(), any(), anyInt());
	}

	@Test
	void offlineAgentCannotClaimWaitingConversation() {
		when(staffService.isCustomerServiceStaff("tenant-1", "staff-1")).thenReturn(true);
		when(agentMapper.selectByStaffId("tenant-1", "staff-1")).thenReturn(agent("staff-1", 0, null));
		when(agentService.isAcceptingNewWork("tenant-1", "staff-1")).thenReturn(false);

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.claim("tenant-1", "conversation-1", "staff-1", null));

		assertEquals("当前离线或未开启接待，无法领取会话", exception.getMsg());
		verify(conversationMapper, never()).assignWaiting(any(), any(), any());
	}

	@Test
	void concurrentClaimOnlyAcceptsSuccessfulConditionalUpdate() {
		when(staffService.isCustomerServiceStaff("tenant-1", "staff-1")).thenReturn(true);
		when(agentMapper.selectByStaffId("tenant-1", "staff-1")).thenReturn(agent("staff-1", 0, null));
		when(agentMapper.incrementActiveCount("tenant-1", "staff-1")).thenReturn(1);
		when(agentService.isAcceptingNewWork("tenant-1", "staff-1")).thenReturn(true);
		when(conversationMapper.assignWaiting("tenant-1", "conversation-1", "staff-1")).thenReturn(0);

		assertThrows(ArynBusinessException.class,
				() -> service.claim("tenant-1", "conversation-1", "staff-1", null));
	}

	@Test
	void onlineAgentClaimRecordsClaimOperator() {
		when(staffService.isCustomerServiceStaff("tenant-1", "staff-1")).thenReturn(true);
		when(agentMapper.selectByStaffId("tenant-1", "staff-1")).thenReturn(agent("staff-1", 0, null));
		when(agentService.isAcceptingNewWork("tenant-1", "staff-1")).thenReturn(true);
		when(agentMapper.incrementActiveCount("tenant-1", "staff-1")).thenReturn(1);
		when(conversationMapper.assignWaiting("tenant-1", "conversation-1", "staff-1")).thenReturn(1);
		when(conversationMapper.selectByIdForUpdate("tenant-1", "conversation-1")).thenReturn(conversation());

		service.claim("tenant-1", "conversation-1", "staff-1", "手动领取");

		ArgumentCaptor<MessageAssignmentLog> captor = ArgumentCaptor.forClass(MessageAssignmentLog.class);
		verify(logMapper).insert(captor.capture());
		assertEquals("CLAIM", captor.getValue().getActionType());
		assertEquals("SYS_USER", captor.getValue().getOperatorType());
		assertEquals("staff-1", captor.getValue().getOperatorId());
	}

	@Test
	void transferToOfflineTargetIsRejectedForNonSupervisor() {
		when(staffService.isCustomerServiceStaff("tenant-1", "staff-2")).thenReturn(true);
		when(agentMapper.selectByStaffId("tenant-1", "staff-2")).thenReturn(agent("staff-2", 0, null));
		when(agentService.isAcceptingNewWork("tenant-1", "staff-2")).thenReturn(false);

		assertThrows(ArynBusinessException.class,
				() -> service.transfer("tenant-1", "conversation-1", "staff-1", "staff-2", null, false));
		verify(conversationMapper, never()).selectByIdForUpdate(any(), any());
	}

	@Test
	void supervisorForceAssignAllowsOfflineTargetAndAuditsForceAssign() {
		MessageConversation conversation = conversation();
		conversation.setAssignedStaffId("staff-1");
		conversation.setStatus(ConversationStatus.ACTIVE.name());
		when(staffService.isCustomerServiceStaff("tenant-1", "staff-2")).thenReturn(true);
		when(agentMapper.selectByStaffId("tenant-1", "staff-2")).thenReturn(agent("staff-2", 0, null));
		when(conversationMapper.selectByIdForUpdate("tenant-1", "conversation-1")).thenReturn(conversation);
		when(agentMapper.incrementActiveCount("tenant-1", "staff-2")).thenReturn(1);

		service.transfer("tenant-1", "conversation-1", "supervisor-1", "staff-2", "客诉升级", true);

		// 主管强制分配允许离线目标，但审计动作必须是 FORCE_ASSIGN。
		verify(agentService, never()).isAcceptingNewWork(any(), any());
		ArgumentCaptor<MessageAssignmentLog> captor = ArgumentCaptor.forClass(MessageAssignmentLog.class);
		verify(logMapper).insert(captor.capture());
		assertEquals("FORCE_ASSIGN", captor.getValue().getActionType());
		assertEquals("staff-1", captor.getValue().getFromStaffId());
		assertEquals("staff-2", captor.getValue().getToStaffId());
		verify(chatMessageService).sendSystem(eq("tenant-1"), eq("conversation-1"), eq("会话已转交"), any(), any());
		verify(agentMapper).decrementActiveCount("tenant-1", "staff-1");
	}

	@Test
	void ordinaryTransferOnlineTargetAuditsTransfer() {
		MessageConversation conversation = conversation();
		conversation.setAssignedStaffId("staff-1");
		conversation.setStatus(ConversationStatus.ACTIVE.name());
		when(staffService.isCustomerServiceStaff("tenant-1", "staff-2")).thenReturn(true);
		when(agentMapper.selectByStaffId("tenant-1", "staff-2")).thenReturn(agent("staff-2", 0, null));
		when(agentService.isAcceptingNewWork("tenant-1", "staff-2")).thenReturn(true);
		when(conversationMapper.selectByIdForUpdate("tenant-1", "conversation-1")).thenReturn(conversation);
		when(agentMapper.incrementActiveCount("tenant-1", "staff-2")).thenReturn(1);

		service.transfer("tenant-1", "conversation-1", "staff-1", "staff-2", null, false);

		ArgumentCaptor<MessageAssignmentLog> captor = ArgumentCaptor.forClass(MessageAssignmentLog.class);
		verify(logMapper).insert(captor.capture());
		assertEquals("TRANSFER", captor.getValue().getActionType());
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

	@Test
	void reopenClearsStaleAssignmentWithoutIgnoringNullUpdate() {
		MessageConversation closed = conversation();
		closed.setStatus(ConversationStatus.CLOSED.name());
		closed.setAssignedStaffId("staff-1");
		closed.setReopenDeadline(LocalDateTime.now().plusHours(1));
		when(conversationMapper.selectRecentlyClosedCustomerConversation(org.mockito.ArgumentMatchers.eq("tenant-1"),
				org.mockito.ArgumentMatchers.eq("member-1"), org.mockito.ArgumentMatchers.eq("DEFAULT"), any()))
				.thenReturn(closed);
		when(conversationMapper.selectByIdForUpdate("tenant-1", "conversation-1")).thenReturn(closed);
		when(conversationMapper.reopenToWaiting("tenant-1", "conversation-1", "member-1")).thenReturn(1);
		when(staffService.isCustomerServiceStaff("tenant-1", "staff-1")).thenReturn(true);
		when(agentService.isAutoAssignable("tenant-1", "staff-1")).thenReturn(false);

		String reopened = service.reopenCustomerConversation("tenant-1", "member-1", "DEFAULT");

		assertEquals("conversation-1", reopened);
		verify(conversationMapper).reopenToWaiting("tenant-1", "conversation-1", "member-1");
		// 回归守卫：updateById 会忽略 null 字段，残留 assigned_staff_id 导致会话永远无法领取。
		verify(conversationMapper, never()).updateById(any(MessageConversation.class));
		verify(participantMapper).deactivate("tenant-1", "conversation-1", MessageIdentityType.SYS_USER.name(),
				"staff-1");
	}

	@Test
	void reassignWaitingPoolRecoversQueuedConversations() {
		MessageConversation first = conversation();
		MessageConversation second = conversation();
		second.setId("conversation-2");
		when(conversationMapper.selectWaitingForTenant("tenant-1", 10)).thenReturn(List.of(first, second));
		// 第一条成功自动分配，第二条候选择空（无人可接），回收数应为 1。
		when(agentMapper.selectAssignmentCandidates("tenant-1", 200))
			.thenReturn(List.of(agent("staff-1", 0, null)))
			.thenReturn(List.of());
		when(agentService.isAutoAssignable("tenant-1", "staff-1")).thenReturn(true);
		when(staffService.isCustomerServiceStaff("tenant-1", "staff-1")).thenReturn(true);
		when(agentMapper.incrementActiveCount("tenant-1", "staff-1")).thenReturn(1);
		when(conversationMapper.assignWaiting("tenant-1", "conversation-1", "staff-1")).thenReturn(1);
		when(conversationMapper.selectByIdForUpdate("tenant-1", "conversation-1")).thenReturn(first);

		int reassigned = service.reassignWaitingPool("tenant-1", 10);

		assertEquals(1, reassigned);
		verify(conversationMapper).selectWaitingForTenant("tenant-1", 10);
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
