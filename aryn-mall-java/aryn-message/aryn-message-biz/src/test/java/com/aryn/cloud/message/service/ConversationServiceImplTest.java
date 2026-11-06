package com.aryn.cloud.message.service;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.dto.conversation.ConversationInboxQuery;
import com.aryn.cloud.message.api.entity.MessageAgent;
import com.aryn.cloud.message.api.entity.MessageConversation;
import com.aryn.cloud.message.api.entity.MessageParticipant;
import com.aryn.cloud.message.api.enums.ConversationStatus;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ConversationAssignmentLogVO;
import com.aryn.cloud.message.api.vo.conversation.ConversationAttentionVO;
import com.aryn.cloud.message.api.vo.conversation.ConversationInboxPageVO;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;
import com.aryn.cloud.message.mapper.MessageAssignmentLogMapper;
import com.aryn.cloud.message.mapper.MessageConversationMapper;
import com.aryn.cloud.message.mapper.MessageParticipantMapper;
import com.aryn.cloud.message.service.impl.ConversationServiceImpl;
import com.aryn.cloud.message.service.impl.CustomerSnapshotService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationServiceImplTest {

	private MessageConversationMapper conversationMapper;
	private MessageParticipantMapper participantMapper;
	private MessageAssignmentLogMapper assignmentLogMapper;
	private ConversationAssignmentService assignmentService;
	private AgentService agentService;
	private CustomerSnapshotService customerSnapshotService;
	private ConversationServiceImpl service;

	@BeforeEach
	void setUp() {
		conversationMapper = mock(MessageConversationMapper.class);
		participantMapper = mock(MessageParticipantMapper.class);
		assignmentLogMapper = mock(MessageAssignmentLogMapper.class);
		assignmentService = mock(ConversationAssignmentService.class);
		agentService = mock(AgentService.class);
		customerSnapshotService = mock(CustomerSnapshotService.class);
		service = new ConversationServiceImpl(conversationMapper, participantMapper, assignmentLogMapper,
				new ObjectMapper(), assignmentService, agentService,
				mock(com.aryn.cloud.user.api.remote.RemoteMessageAudienceService.class), customerSnapshotService);
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
	void reenteringWaitingConversationDoesNotAssignInsideCreationTransaction() {
		MessageConversation waiting = conversation(3L);
		waiting.setStatus(ConversationStatus.WAITING.name());
		ConversationVO view = new ConversationVO();
		view.setId("conversation-1");
		when(conversationMapper.selectActiveCustomerConversation("tenant-1", "member-1", "DEFAULT"))
			.thenReturn(waiting);
		when(conversationMapper.selectForParticipant("tenant-1", "MALL_USER", "member-1", "conversation-1"))
			.thenReturn(view);

		service.getOrCreateCustomerService("tenant-1", "member-1", "会员", null, null, null);

		// 自动分配重试由接口层在事务提交后执行：服务内层再调 TransactionTemplate 会在并发领取时
		// 把外层事务标记 rollback-only，接口整体 500，因此这里守卫「创建事务内不自动分配」。
		verify(assignmentService, never()).autoAssign(any(), any());
	}

	@Test
	void assignedConversationIsNotAutoAssignedAgainOnReentry() {
		MessageConversation assigned = conversation(3L);
		assigned.setStatus(ConversationStatus.ACTIVE.name());
		assigned.setAssignedStaffId("staff-1");
		when(conversationMapper.selectActiveCustomerConversation("tenant-1", "member-1", "DEFAULT"))
			.thenReturn(assigned);
		when(conversationMapper.selectForParticipant("tenant-1", "MALL_USER", "member-1", "conversation-1"))
			.thenReturn(new ConversationVO());

		service.getOrCreateCustomerService("tenant-1", "member-1", "会员", null, null, null);

		verify(assignmentService, never()).autoAssign(any(), any());
	}

	@Test
	void inboxDecodesAndReencodesOpaqueCursorWithStatusFilter() {
		LocalDateTime base = LocalDateTime.of(2026, 11, 4, 10, 30, 5);
		List<ConversationVO> firstPage = new ArrayList<>();
		for (int index = 0; index < 21; index++) {
			ConversationVO view = new ConversationVO();
			view.setId("conversation-" + index);
			view.setLastActiveTime(base.minusMinutes(index));
			firstPage.add(view);
		}
		when(conversationMapper.selectInbox("tenant-1", "SYS_USER", "staff-1", "OPEN", null, null, null, null,
				false, null, null, 21)).thenReturn(firstPage);

		ConversationInboxQuery query = new ConversationInboxQuery();
		query.setStatus("open");
		query.setLimit(20);
		ConversationInboxPageVO page = service.inbox("tenant-1", MessageIdentityType.SYS_USER, "staff-1", query);

		assertEquals(20, page.getRecords().size());
		assertTrue(page.isHasMore());
		assertNotNull(page.getNextCursor());
		assertEquals("conversation-19", page.getRecords().get(19).getId());

		// 前端原样回传 nextCursor：服务端应还原成 lastActiveTime + id 的复合游标。
		ConversationInboxQuery nextQuery = new ConversationInboxQuery();
		nextQuery.setStatus("OPEN");
		nextQuery.setLimit(20);
		nextQuery.setCursor(page.getNextCursor());
		when(conversationMapper.selectInbox("tenant-1", "SYS_USER", "staff-1", "OPEN", null, null, null, null,
				false, base.minusMinutes(19), "conversation-19", 21)).thenReturn(List.of());
		ConversationInboxPageVO nextPage = service.inbox("tenant-1", MessageIdentityType.SYS_USER, "staff-1",
				nextQuery);

		assertTrue(nextPage.getRecords().isEmpty());
		assertFalse(nextPage.isHasMore());
		assertNull(nextPage.getNextCursor());
		verify(conversationMapper).selectInbox("tenant-1", "SYS_USER", "staff-1", "OPEN", null, null, null, null,
				false, base.minusMinutes(19), "conversation-19", 21);
	}

	@Test
	void inboxRejectsUnknownStatusAndTamperedCursor() {
		ConversationInboxQuery badStatus = new ConversationInboxQuery();
		badStatus.setStatus("ARCHIVED");
		assertThrows(ArynBusinessException.class,
				() -> service.inbox("tenant-1", MessageIdentityType.SYS_USER, "staff-1", badStatus));

		ConversationInboxQuery badCursor = new ConversationInboxQuery();
		badCursor.setCursor("not-a-valid-cursor");
		assertThrows(ArynBusinessException.class,
				() -> service.inbox("tenant-1", MessageIdentityType.SYS_USER, "staff-1", badCursor));
		verify(conversationMapper, never()).selectInbox(any(), any(), any(), any(), any(), any(), any(), any(),
				anyBoolean(), any(), any(), anyInt());
	}

	@Test
	void adminInboxQueriesTenantWideIncludingClosed() {
		ConversationInboxQuery query = new ConversationInboxQuery();
		query.setStatus("CLOSED");
		query.setLimit(10);
		when(conversationMapper.selectTenantInbox("tenant-1", "CLOSED", null, null, null, null, false, null, null,
				11)).thenReturn(List.of());

		ConversationInboxPageVO page = service.adminInbox("tenant-1", query);

		assertTrue(page.getRecords().isEmpty());
		assertFalse(page.isHasMore());
		verify(conversationMapper).selectTenantInbox("tenant-1", "CLOSED", null, null, null, null, false, null, null,
				11);
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
	void attentionIncludesWaitingPoolOnlyForOnlineAgent() {
		when(agentService.isAcceptingNewWork("tenant-1", "staff-1")).thenReturn(true);
		when(conversationMapper.selectUnreadForParticipant("tenant-1", "SYS_USER", "staff-1", 10))
			.thenReturn(new ArrayList<>());
		when(conversationMapper.countUnreadForParticipant("tenant-1", "SYS_USER", "staff-1")).thenReturn(2L);
		when(conversationMapper.countWaiting("tenant-1", "DEFAULT")).thenReturn(3L);
		ConversationVO waiting = new ConversationVO();
		waiting.setId("conversation-waiting");
		when(conversationMapper.selectWaitingPool("tenant-1", "DEFAULT", 10)).thenReturn(List.of(waiting));

		ConversationAttentionVO attention = service.attention("tenant-1", MessageIdentityType.SYS_USER, "staff-1",
				"DEFAULT", 10);

		assertEquals(2L, attention.getUnreadConversations());
		assertEquals(3L, attention.getWaitingTotal());
		assertEquals(List.of("conversation-waiting"),
				attention.getConversations().stream().map(ConversationVO::getId).toList());
	}

	@Test
	void attentionSkipsWaitingPoolForEnabledButOfflineAgent() {
		// 离线坐席仍能看到自已的未读会话，但共享池待领取提醒不应出现（接待状态只约束承接新工作）。
		when(agentService.isAcceptingNewWork("tenant-1", "staff-2")).thenReturn(false);
		when(conversationMapper.selectUnreadForParticipant("tenant-1", "SYS_USER", "staff-2", 10))
			.thenReturn(new ArrayList<>());

		ConversationAttentionVO attention = service.attention("tenant-1", MessageIdentityType.SYS_USER, "staff-2",
				"DEFAULT", 10);

		assertEquals(0L, attention.getWaitingTotal());
		verify(conversationMapper, never()).countWaiting(any(), any());
		verify(conversationMapper, never()).selectWaitingPool(any(), any(), anyInt());
	}

	@Test
	void attentionNeverShowsWaitingPoolToMemberIdentity() {
		when(conversationMapper.selectUnreadForParticipant("tenant-1", "MALL_USER", "member-1", 10))
			.thenReturn(new ArrayList<>());

		ConversationAttentionVO attention = service.attention("tenant-1", MessageIdentityType.MALL_USER, "member-1",
				"DEFAULT", 10);

		assertEquals(0L, attention.getWaitingTotal());
		verify(agentService, never()).isAcceptingNewWork(any(), any());
	}

	@Test
	void attentionDoesNotDuplicateConversationsAlreadyUnread() {
		when(agentService.isAcceptingNewWork("tenant-1", "staff-1")).thenReturn(true);
		ConversationVO unread = new ConversationVO();
		unread.setId("conversation-1");
		when(conversationMapper.selectUnreadForParticipant("tenant-1", "SYS_USER", "staff-1", 10))
			.thenReturn(new ArrayList<>(List.of(unread)));
		ConversationVO waiting = new ConversationVO();
		waiting.setId("conversation-1");
		when(conversationMapper.selectWaitingPool("tenant-1", "DEFAULT", 10)).thenReturn(List.of(waiting));

		ConversationAttentionVO attention = service.attention("tenant-1", MessageIdentityType.SYS_USER, "staff-1",
				"DEFAULT", 10);

		assertEquals(List.of("conversation-1"),
				attention.getConversations().stream().map(ConversationVO::getId).toList());
	}

	@Test
	void assignmentLogsAreTenantBound() {
		ConversationAssignmentLogVO log = new ConversationAssignmentLogVO();
		log.setActionType("FORCE_ASSIGN");
		when(assignmentLogMapper.selectByConversation("tenant-1", "conversation-1")).thenReturn(List.of(log));

		List<ConversationAssignmentLogVO> logs = service.assignmentLogs("tenant-1", "conversation-1");

		assertEquals(1, logs.size());
		assertEquals("FORCE_ASSIGN", logs.get(0).getActionType());
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
