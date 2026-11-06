package com.aryn.cloud.message.service;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.message.api.entity.MessageAgent;
import com.aryn.cloud.message.api.enums.AgentPresenceStatus;
import com.aryn.cloud.message.api.vo.agent.AgentCapacityVO;
import com.aryn.cloud.message.api.vo.agent.AgentRosterVO;
import com.aryn.cloud.message.mapper.MessageAgentMapper;
import com.aryn.cloud.message.service.impl.AgentServiceImpl;
import com.aryn.cloud.upms.api.remote.RemoteMessageStaffService;
import com.aryn.cloud.upms.api.vo.StaffMessageAudiencePageVO;
import com.aryn.cloud.upms.api.vo.StaffMessageRecipientVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * P2-A 在线坐席名单（/staff/agent/roster）与租户坐席容量测试。
 *
 * <p>锁住口径：presence 批量 MGET、Redis 异常全离线降级、upms 异常只丢昵称不丢名单、
 * 禁用坐席强制 OFFLINE、available 计算与排序、空配置不触发远程调用；容量只统计启用且 ONLINE。</p>
 */
class AgentRosterServiceTest {

	private MessageAgentMapper agentMapper;
	private RemoteMessageStaffService staffService;
	private StringRedisTemplate redisTemplate;
	private ValueOperations<String, String> valueOperations;
	private AgentServiceImpl service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		agentMapper = mock(MessageAgentMapper.class);
		staffService = mock(RemoteMessageStaffService.class);
		redisTemplate = mock(StringRedisTemplate.class);
		valueOperations = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		service = new AgentServiceImpl(agentMapper, redisTemplate, mock(ApplicationEventPublisher.class),
				staffService);
	}

	@Test
	void rosterAssemblesUpmsSnapshotPresenceAndCapacity() {
		when(agentMapper.selectAllAgents("tenant-1")).thenReturn(List.of(
				agent("staff-1", CommonConstants.YES, CommonConstants.YES, 2, 5),
				agent("staff-2", CommonConstants.YES, CommonConstants.NO, 5, 5),
				agent("staff-3", CommonConstants.NO, CommonConstants.YES, 0, 10)));
		when(valueOperations.multiGet(List.of("message:agent:presence:tenant-1:staff-1",
				"message:agent:presence:tenant-1:staff-2")))
			.thenReturn(Arrays.asList(AgentPresenceStatus.ONLINE.name(), AgentPresenceStatus.BUSY.name()));
		when(staffService.queryRecipients(any())).thenReturn(staffPage(recipient("staff-1", "客服小美", "a1.png"),
				recipient("staff-2", "客服小刚", null), recipient("staff-3", "禁用坐席", "a3.png")));

		List<AgentRosterVO> roster = service.listRoster("tenant-1");

		assertEquals(3, roster.size());
		AgentRosterVO available = roster.get(0);
		assertEquals("staff-1", available.getStaffId());
		assertEquals("客服小美", available.getStaffName());
		assertEquals("a1.png", available.getStaffAvatar());
		assertEquals("ONLINE", available.getPresenceStatus());
		assertEquals("1", available.getEnabled());
		assertEquals("1", available.getAutoAccept());
		assertEquals(2, available.getCurrentActiveCount());
		assertEquals(5, available.getMaxActiveCount());
		assertTrue(available.getAvailable());

		// BUSY 不算可接，排在可接坐席之后。
		AgentRosterVO busy = roster.get(1);
		assertEquals("staff-2", busy.getStaffId());
		assertEquals("客服小刚", busy.getStaffName());
		assertFalse(busy.getAvailable());

		// 禁用坐席按 OFFLINE 展示，昵称/头像仍来自 upms。
		AgentRosterVO disabled = roster.get(2);
		assertEquals("staff-3", disabled.getStaffId());
		assertEquals("禁用坐席", disabled.getStaffName());
		assertEquals("OFFLINE", disabled.getPresenceStatus());
		assertEquals("0", disabled.getEnabled());
		assertFalse(disabled.getAvailable());

		// 禁用坐席不读 Redis：只对 2 个启用坐席做一次 MGET。
		verify(valueOperations).multiGet(List.of("message:agent:presence:tenant-1:staff-1",
				"message:agent:presence:tenant-1:staff-2"));
	}

	@Test
	void rosterDegradesToOfflineWhenRedisUnavailable() {
		when(agentMapper.selectAllAgents("tenant-1"))
			.thenReturn(List.of(agent("staff-1", CommonConstants.YES, CommonConstants.YES, 0, 10)));
		when(valueOperations.multiGet(any())).thenThrow(new IllegalStateException("redis down"));
		when(staffService.queryRecipients(any())).thenReturn(staffPage());

		List<AgentRosterVO> roster = assertDoesNotThrow(() -> service.listRoster("tenant-1"));

		assertEquals(1, roster.size());
		assertEquals("OFFLINE", roster.get(0).getPresenceStatus());
		assertFalse(roster.get(0).getAvailable());
	}

	@Test
	void rosterDegradesToStaffIdOnlyWhenUpmsUnavailable() {
		when(agentMapper.selectAllAgents("tenant-1"))
			.thenReturn(List.of(agent("staff-1", CommonConstants.YES, CommonConstants.YES, 0, 10)));
		when(valueOperations.multiGet(any())).thenReturn(List.of(AgentPresenceStatus.ONLINE.name()));
		when(staffService.queryRecipients(any())).thenThrow(new IllegalStateException("upms down"));

		List<AgentRosterVO> roster = assertDoesNotThrow(() -> service.listRoster("tenant-1"));

		assertEquals(1, roster.size());
		assertNull(roster.get(0).getStaffName());
		assertNull(roster.get(0).getStaffAvatar());
		assertEquals("ONLINE", roster.get(0).getPresenceStatus());
		assertTrue(roster.get(0).getAvailable());
	}

	@Test
	void emptyAgentConfigSkipsRemoteAndRedisCalls() {
		when(agentMapper.selectAllAgents("tenant-1")).thenReturn(List.of());

		assertTrue(service.listRoster("tenant-1").isEmpty());
		verifyNoInteractions(valueOperations);
		verify(staffService, never()).queryRecipients(any());
	}

	@Test
	void rosterSortsLessLoadedAvailableAgentFirst() {
		when(agentMapper.selectAllAgents("tenant-1")).thenReturn(List.of(
				agent("staff-a", CommonConstants.YES, CommonConstants.YES, 4, 5),
				agent("staff-b", CommonConstants.YES, CommonConstants.YES, 1, 5)));
		when(valueOperations.multiGet(any()))
			.thenReturn(List.of(AgentPresenceStatus.ONLINE.name(), AgentPresenceStatus.ONLINE.name()));
		when(staffService.queryRecipients(any())).thenReturn(staffPage());

		List<AgentRosterVO> roster = service.listRoster("tenant-1");

		assertEquals(List.of("staff-b", "staff-a"), roster.stream().map(AgentRosterVO::getStaffId).toList());
	}

	@Test
	void agentCapacityCountsOnlineAgentsAndOnlyOnlineRemainingCapacity() {
		when(agentMapper.selectAllAgents("tenant-1")).thenReturn(List.of(
				agent("staff-1", CommonConstants.YES, CommonConstants.YES, 3, 10),
				agent("staff-2", CommonConstants.YES, CommonConstants.NO, 10, 10),
				agent("staff-3", CommonConstants.YES, CommonConstants.YES, 0, 10),
				agent("staff-4", CommonConstants.NO, CommonConstants.YES, 0, 10)));
		when(valueOperations.multiGet(List.of("message:agent:presence:tenant-1:staff-1",
				"message:agent:presence:tenant-1:staff-2", "message:agent:presence:tenant-1:staff-3")))
			.thenReturn(Arrays.asList(AgentPresenceStatus.ONLINE.name(), AgentPresenceStatus.BUSY.name(), null));

		AgentCapacityVO capacity = service.agentCapacity("tenant-1");

		// ONLINE + BUSY 都算在线；容量只算 ONLINE 的剩余上限（10-3=7）。
		assertEquals(2, capacity.getOnlineAgentCount());
		assertEquals(7, capacity.getAvailableCapacity());
		// 禁用坐席不参与在线统计，也不读 Redis。
		verify(valueOperations).multiGet(List.of("message:agent:presence:tenant-1:staff-1",
				"message:agent:presence:tenant-1:staff-2", "message:agent:presence:tenant-1:staff-3"));
	}

	@Test
	void agentCapacityDegradesToZeroWhenRedisUnavailable() {
		when(agentMapper.selectAllAgents("tenant-1"))
			.thenReturn(List.of(agent("staff-1", CommonConstants.YES, CommonConstants.YES, 3, 10)));
		when(valueOperations.multiGet(any())).thenThrow(new IllegalStateException("redis down"));

		AgentCapacityVO capacity = assertDoesNotThrow(() -> service.agentCapacity("tenant-1"));

		assertEquals(0, capacity.getOnlineAgentCount());
		assertEquals(0, capacity.getAvailableCapacity());
	}

	private MessageAgent agent(String staffId, String enabled, String autoAccept, int activeCount, int maxActiveCount) {
		MessageAgent agent = new MessageAgent();
		agent.setTenantId("tenant-1");
		agent.setStaffId(staffId);
		agent.setEnabled(enabled);
		agent.setAutoAccept(autoAccept);
		agent.setCurrentActiveCount(activeCount);
		agent.setMaxActiveCount(maxActiveCount);
		return agent;
	}

	private StaffMessageRecipientVO recipient(String id, String nickname, String avatar) {
		StaffMessageRecipientVO recipient = new StaffMessageRecipientVO();
		recipient.setId(id);
		recipient.setNickname(nickname);
		recipient.setAvatar(avatar);
		return recipient;
	}

	private StaffMessageAudiencePageVO staffPage(StaffMessageRecipientVO... recipients) {
		StaffMessageAudiencePageVO page = new StaffMessageAudiencePageVO();
		page.setRecords(Arrays.asList(recipients));
		return page;
	}

}
