package com.aryn.cloud.message.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.mapper.MessageAgentMapper;
import com.aryn.cloud.message.service.impl.AgentServiceImpl;
import com.aryn.cloud.upms.api.dto.StaffMessageAudienceRequest;
import com.aryn.cloud.upms.api.remote.RemoteMessageStaffService;
import com.aryn.cloud.upms.api.vo.StaffMessageAudiencePageVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentServiceImplTest {

	private MessageAgentMapper agentMapper;
	private RemoteMessageStaffService staffService;
	private AgentServiceImpl service;

	@BeforeEach
	void setUp() {
		agentMapper = mock(MessageAgentMapper.class);
		staffService = mock(RemoteMessageStaffService.class);
		service = new AgentServiceImpl(agentMapper, mock(StringRedisTemplate.class), staffService);
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

}
