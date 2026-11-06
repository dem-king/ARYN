package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.vo.agent.AgentCapacityVO;
import com.aryn.cloud.message.api.vo.conversation.QueueStatsGroupVO;
import com.aryn.cloud.message.api.vo.conversation.QueueStatsVO;
import com.aryn.cloud.message.mapper.MessageAssignmentLogMapper;
import com.aryn.cloud.message.mapper.MessageConversationMapper;
import com.aryn.cloud.message.mapper.MessageParticipantMapper;
import com.aryn.cloud.message.service.impl.ConversationServiceImpl;
import com.aryn.cloud.user.api.remote.RemoteMessageAudienceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2-A 队列统计（/staff/conversation/queue-stats）聚合测试。
 *
 * <p>锁住口径：等待数按 queueCode 分组求和、最久等待取 MIN、无等待时 total 为 0/null 且
 * groups 为空、Redis 降级（在线/容量为 0）不影响等待统计。</p>
 */
class QueueStatsServiceTest {

	private MessageConversationMapper conversationMapper;
	private AgentService agentService;
	private ConversationServiceImpl service;

	@BeforeEach
	void setUp() {
		conversationMapper = mock(MessageConversationMapper.class);
		agentService = mock(AgentService.class);
		service = new ConversationServiceImpl(conversationMapper, mock(MessageParticipantMapper.class),
				mock(MessageAssignmentLogMapper.class), new ObjectMapper(), mock(ConversationAssignmentService.class),
				agentService, mock(RemoteMessageAudienceService.class));
	}

	@Test
	void aggregatesGroupedWaitingAndTenantAgentCapacity() {
		LocalDateTime oldest = LocalDateTime.of(2026, 11, 6, 10, 40, 0);
		LocalDateTime second = LocalDateTime.of(2026, 11, 6, 11, 2, 31);
		when(conversationMapper.selectWaitingGroups("tenant-1"))
			.thenReturn(List.of(group("DEFAULT", 4L, oldest), group("VIP", 1L, second)));
		when(agentService.agentCapacity("tenant-1")).thenReturn(capacity(3, 12));

		QueueStatsVO stats = service.queueStats("tenant-1");

		assertEquals(5L, stats.getTotal().getWaitingCount().longValue());
		assertEquals(oldest, stats.getTotal().getOldestWaitingSince());
		assertEquals(3, stats.getTotal().getOnlineAgentCount());
		assertEquals(12, stats.getTotal().getAvailableCapacity());
		assertEquals(2, stats.getGroups().size());
		assertEquals("DEFAULT", stats.getGroups().get(0).getQueueCode());
		verify(agentService).agentCapacity("tenant-1");
	}

	@Test
	void oldestWaitingSinceIsEarliestAcrossUnorderedGroups() {
		LocalDateTime earlier = LocalDateTime.of(2026, 11, 6, 9, 0, 0);
		LocalDateTime later = LocalDateTime.of(2026, 11, 6, 11, 0, 0);
		when(conversationMapper.selectWaitingGroups("tenant-1"))
			.thenReturn(List.of(group("VIP", 2L, later), group("DEFAULT", 1L, earlier)));
		when(agentService.agentCapacity("tenant-1")).thenReturn(capacity(1, 4));

		QueueStatsVO stats = service.queueStats("tenant-1");

		assertEquals(3L, stats.getTotal().getWaitingCount().longValue());
		assertEquals(earlier, stats.getTotal().getOldestWaitingSince());
	}

	@Test
	void noWaitingConversationsReturnsZeroTotalAndEmptyGroups() {
		when(conversationMapper.selectWaitingGroups("tenant-1")).thenReturn(List.of());
		when(agentService.agentCapacity("tenant-1")).thenReturn(capacity(2, 0));

		QueueStatsVO stats = service.queueStats("tenant-1");

		assertEquals(0L, stats.getTotal().getWaitingCount().longValue());
		assertNull(stats.getTotal().getOldestWaitingSince());
		assertEquals(2, stats.getTotal().getOnlineAgentCount());
		assertEquals(0, stats.getTotal().getAvailableCapacity());
		assertTrue(stats.getGroups().isEmpty());
	}

	@Test
	void redisDegradedCapacityKeepsWaitingStats() {
		LocalDateTime oldest = LocalDateTime.of(2026, 11, 6, 10, 0, 0);
		when(conversationMapper.selectWaitingGroups("tenant-1"))
			.thenReturn(List.of(group("DEFAULT", 3L, oldest)));
		// Redis 不可用时 AgentService 降级为 0/0，等待统计不受影响。
		when(agentService.agentCapacity("tenant-1")).thenReturn(capacity(0, 0));

		QueueStatsVO stats = service.queueStats("tenant-1");

		assertEquals(3L, stats.getTotal().getWaitingCount().longValue());
		assertEquals(oldest, stats.getTotal().getOldestWaitingSince());
		assertEquals(0, stats.getTotal().getOnlineAgentCount());
		assertEquals(0, stats.getTotal().getAvailableCapacity());
	}

	@Test
	void nullMapperAndCapacityResultsAreTreatedAsEmpty() {
		when(conversationMapper.selectWaitingGroups("tenant-1")).thenReturn(null);
		when(agentService.agentCapacity("tenant-1")).thenReturn(null);

		QueueStatsVO stats = service.queueStats("tenant-1");

		assertEquals(0L, stats.getTotal().getWaitingCount().longValue());
		assertNull(stats.getTotal().getOldestWaitingSince());
		assertEquals(0, stats.getTotal().getOnlineAgentCount());
		assertEquals(0, stats.getTotal().getAvailableCapacity());
		assertTrue(stats.getGroups().isEmpty());
	}

	private QueueStatsGroupVO group(String queueCode, Long waitingCount, LocalDateTime oldestWaitingSince) {
		QueueStatsGroupVO group = new QueueStatsGroupVO();
		group.setQueueCode(queueCode);
		group.setWaitingCount(waitingCount);
		group.setOldestWaitingSince(oldestWaitingSince);
		return group;
	}

	private AgentCapacityVO capacity(int onlineAgentCount, int availableCapacity) {
		AgentCapacityVO capacity = new AgentCapacityVO();
		capacity.setOnlineAgentCount(onlineAgentCount);
		capacity.setAvailableCapacity(availableCapacity);
		return capacity;
	}

}
