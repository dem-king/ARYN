package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.entity.MessageAgent;
import com.aryn.cloud.message.api.entity.MessageConversation;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.mapper.MessageAgentMapper;
import com.aryn.cloud.message.mapper.MessageConversationMapper;
import com.aryn.cloud.message.service.impl.ConversationMaintenanceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationMaintenanceServiceImplTest {

	private MessageConversationMapper conversationMapper;
	private MessageAgentMapper agentMapper;
	private ConversationAssignmentService assignmentService;
	private ConversationMaintenanceServiceImpl service;

	@BeforeEach
	void setUp() {
		conversationMapper = mock(MessageConversationMapper.class);
		agentMapper = mock(MessageAgentMapper.class);
		assignmentService = mock(ConversationAssignmentService.class);
		service = new ConversationMaintenanceServiceImpl(conversationMapper, agentMapper, assignmentService);
	}

	@Test
	void maintainTenantClosesInactiveRecoversPoolAndRepairsActiveCounts() {
		MessageConversation inactive = conversation("conversation-1");
		when(conversationMapper.selectInactiveConversationsByTenant(eq("tenant-1"), any(), eq(100)))
			.thenReturn(List.of(inactive));
		when(assignmentService.reassignWaitingPool("tenant-1", 10)).thenReturn(2);
		MessageAgent drifted = agent("staff-1", 5);
		MessageAgent consistent = agent("staff-2", 0);
		when(agentMapper.selectAllAgents("tenant-1")).thenReturn(List.of(drifted, consistent));
		when(conversationMapper.countActiveByStaff("tenant-1", "staff-1")).thenReturn(3L);
		when(conversationMapper.countActiveByStaff("tenant-1", "staff-2")).thenReturn(0L);
		when(agentMapper.updateActiveCount("tenant-1", "staff-1", 3)).thenReturn(1);

		Map<String, Object> stat = service.maintainTenant("tenant-1");

		assertEquals(1, stat.get("closed"));
		assertEquals(2, stat.get("reassigned"));
		assertEquals(1, stat.get("repaired"));
		verify(assignmentService).close("tenant-1", "conversation-1", MessageIdentityType.SYSTEM, "SYSTEM",
				"超过 24 小时无新消息自动关闭", true);
		verify(agentMapper).updateActiveCount("tenant-1", "staff-1", 3);
		// 快照已一致（0 == 0）的坐席不应被更新。
		verify(agentMapper, never()).updateActiveCount("tenant-1", "staff-2", 0);
	}

	@Test
	void singleCloseFailureDoesNotBlockOtherMaintenanceSteps() {
		MessageConversation inactive = conversation("conversation-1");
		when(conversationMapper.selectInactiveConversationsByTenant(eq("tenant-1"), any(), eq(100)))
			.thenReturn(List.of(inactive));
		org.mockito.Mockito.doThrow(new IllegalStateException("db conflict")).when(assignmentService).close(
				eq("tenant-1"), eq("conversation-1"), eq(MessageIdentityType.SYSTEM), eq("SYSTEM"), any(), eq(true));
		when(agentMapper.selectAllAgents("tenant-1")).thenReturn(List.of());

		Map<String, Object> stat = service.maintainTenant("tenant-1");

		assertEquals(0, stat.get("closed"));
		// 关闭失败不阻断待领取回收。
		verify(assignmentService).reassignWaitingPool("tenant-1", 10);
		verify(agentMapper).selectAllAgents("tenant-1");
	}

	@Test
	void poolRecoveryFailureIsIsolated() {
		when(conversationMapper.selectInactiveConversationsByTenant(eq("tenant-1"), any(), eq(100)))
			.thenReturn(List.of());
		when(assignmentService.reassignWaitingPool("tenant-1", 10))
			.thenThrow(new IllegalStateException("assign failed"));
		when(agentMapper.selectAllAgents("tenant-1")).thenReturn(List.of());

		Map<String, Object> stat = service.maintainTenant("tenant-1");

		assertEquals(0, stat.get("reassigned"));
		verify(agentMapper).selectAllAgents("tenant-1");
	}

	private MessageConversation conversation(String id) {
		MessageConversation conversation = new MessageConversation();
		conversation.setId(id);
		conversation.setTenantId("tenant-1");
		conversation.setCreateTime(LocalDateTime.now().minusDays(2));
		return conversation;
	}

	private MessageAgent agent(String staffId, int currentActiveCount) {
		MessageAgent agent = new MessageAgent();
		agent.setTenantId("tenant-1");
		agent.setStaffId(staffId);
		agent.setEnabled("1");
		agent.setCurrentActiveCount(currentActiveCount);
		agent.setMaxActiveCount(10);
		return agent;
	}

}
