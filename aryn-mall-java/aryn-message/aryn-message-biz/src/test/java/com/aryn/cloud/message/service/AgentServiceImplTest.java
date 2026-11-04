package com.aryn.cloud.message.service;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.entity.MessageAgent;
import com.aryn.cloud.message.api.enums.AgentPresenceStatus;
import com.aryn.cloud.message.mapper.MessageAgentMapper;
import com.aryn.cloud.message.service.event.AgentOnlineEvent;
import com.aryn.cloud.message.service.impl.AgentServiceImpl;
import com.aryn.cloud.upms.api.dto.StaffMessageAudienceRequest;
import com.aryn.cloud.upms.api.remote.RemoteMessageStaffService;
import com.aryn.cloud.upms.api.vo.StaffMessageAudiencePageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentServiceImplTest {

	private MessageAgentMapper agentMapper;
	private RemoteMessageStaffService staffService;
	private StringRedisTemplate redisTemplate;
	private ValueOperations<String, String> valueOperations;
	private ApplicationEventPublisher eventPublisher;
	private AgentServiceImpl service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		agentMapper = mock(MessageAgentMapper.class);
		staffService = mock(RemoteMessageStaffService.class);
		redisTemplate = mock(StringRedisTemplate.class);
		valueOperations = mock(ValueOperations.class);
		eventPublisher = mock(ApplicationEventPublisher.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		service = new AgentServiceImpl(agentMapper, redisTemplate, eventPublisher, staffService);
	}

	@Test
	void missingOptionalConfigReturnsNull() {
		when(agentMapper.selectByStaffId("tenant-1", "staff-1")).thenReturn(null);

		assertNull(service.findConfig("tenant-1", "staff-1"));
		assertThrows(ArynBusinessException.class, () -> service.get("tenant-1", "staff-1"));
	}

	@Test
	void candidatesOnlyIncludeQualifiedCustomerServiceStaff() {
		StaffMessageAudiencePageVO page = new StaffMessageAudiencePageVO();
		when(staffService.queryRecipients(org.mockito.ArgumentMatchers.any())).thenReturn(page);

		assertEquals(page, service.listCandidates("tenant-1"));

		ArgumentCaptor<StaffMessageAudienceRequest> captor = ArgumentCaptor
			.forClass(StaffMessageAudienceRequest.class);
		verify(staffService).queryRecipients(captor.capture());
		assertEquals("tenant-1", captor.getValue().getTenantId());
		assertEquals(500, captor.getValue().getLimit());
		assertTrue(captor.getValue().getCustomerServiceOnly());
	}

	@Test
	void enabledAgentOnlyAcceptsNewWorkWhenOnline() {
		when(agentMapper.selectByStaffId("tenant-1", "staff-1")).thenReturn(agent(CommonConstants.YES));
		// Redis 无 presence 值 = 离线：启用坐席可以回复已分配会话，但不能承接新工作。
		when(valueOperations.get("message:agent:presence:tenant-1:staff-1")).thenReturn(null);

		assertTrue(service.isEnabledAgent("tenant-1", "staff-1"));
		assertFalse(service.isAcceptingNewWork("tenant-1", "staff-1"));

		when(valueOperations.get("message:agent:presence:tenant-1:staff-1"))
			.thenReturn(AgentPresenceStatus.ONLINE.name());
		assertTrue(service.isAcceptingNewWork("tenant-1", "staff-1"));
	}

	@Test
	void disabledAgentNeverAcceptsNewWork() {
		when(agentMapper.selectByStaffId("tenant-1", "staff-1")).thenReturn(agent(CommonConstants.NO));
		when(valueOperations.get("message:agent:presence:tenant-1:staff-1"))
			.thenReturn(AgentPresenceStatus.ONLINE.name());

		assertFalse(service.isEnabledAgent("tenant-1", "staff-1"));
		assertFalse(service.isAcceptingNewWork("tenant-1", "staff-1"));
	}

	@Test
	void filterOnlineUsesSingleBatchPresenceLookup() {
		when(valueOperations.multiGet(List.of("message:agent:presence:tenant-1:staff-1",
				"message:agent:presence:tenant-1:staff-2", "message:agent:presence:tenant-1:staff-3")))
			.thenReturn(java.util.Arrays.asList(AgentPresenceStatus.ONLINE.name(), null,
					AgentPresenceStatus.BUSY.name()));

		List<String> online = service.filterOnline("tenant-1", List.of("staff-1", "staff-2", "staff-3"));

		assertEquals(List.of("staff-1"), online);
		verify(valueOperations).multiGet(any());
	}

	@Test
	void filterOnlineDegradesToNobodyWhenRedisUnavailable() {
		when(valueOperations.multiGet(any())).thenThrow(new IllegalStateException("redis down"));

		assertTrue(service.filterOnline("tenant-1", List.of("staff-1")).isEmpty());
	}

	@Test
	void goingOnlinePublishesAgentOnlineEvent() {
		when(agentMapper.selectByStaffId("tenant-1", "staff-1")).thenReturn(agent(CommonConstants.YES));
		when(valueOperations.get("message:agent:presence:tenant-1:staff-1")).thenReturn(null);

		service.setPresence("tenant-1", "staff-1", AgentPresenceStatus.ONLINE);

		ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(captor.capture());
		AgentOnlineEvent online = (AgentOnlineEvent) captor.getValue();
		assertEquals("tenant-1", online.tenantId());
		assertEquals("staff-1", online.staffId());
	}

	@Test
	void goingOfflineDoesNotPublishOnlineEvent() {
		when(agentMapper.selectByStaffId("tenant-1", "staff-1")).thenReturn(agent(CommonConstants.YES));
		when(valueOperations.get("message:agent:presence:tenant-1:staff-1"))
			.thenReturn(AgentPresenceStatus.ONLINE.name());

		service.setPresence("tenant-1", "staff-1", AgentPresenceStatus.OFFLINE);

		verify(eventPublisher, never()).publishEvent(any(Object.class));
	}

	@Test
	void presenceUpdateRequiresEnabledAgent() {
		when(agentMapper.selectByStaffId("tenant-1", "staff-1")).thenReturn(agent(CommonConstants.NO));

		assertThrows(ArynBusinessException.class,
				() -> service.setPresence("tenant-1", "staff-1", AgentPresenceStatus.ONLINE));
		verify(redisTemplate, never()).delete(anyString());
	}

	private MessageAgent agent(String enabled) {
		MessageAgent agent = new MessageAgent();
		agent.setTenantId("tenant-1");
		agent.setStaffId("staff-1");
		agent.setEnabled(enabled);
		agent.setAutoAccept(CommonConstants.YES);
		agent.setCurrentActiveCount(0);
		agent.setMaxActiveCount(10);
		return agent;
	}

}
