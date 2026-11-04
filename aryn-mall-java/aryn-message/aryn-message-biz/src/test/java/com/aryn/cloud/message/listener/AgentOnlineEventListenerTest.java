package com.aryn.cloud.message.listener;

import com.aryn.cloud.message.service.ConversationAssignmentService;
import com.aryn.cloud.message.service.event.AgentOnlineEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentOnlineEventListenerTest {

	@Test
	void goingOnlineTriggersBestEffortPoolRecovery() {
		ConversationAssignmentService assignmentService = mock(ConversationAssignmentService.class);
		when(assignmentService.reassignWaitingPool("tenant-1", 10)).thenReturn(3);
		AgentOnlineEventListener listener = new AgentOnlineEventListener(assignmentService);

		listener.onAgentOnline(new AgentOnlineEvent("tenant-1", "staff-1"));

		verify(assignmentService).reassignWaitingPool("tenant-1", 10);
	}

	@Test
	void poolRecoveryFailureNeverBreaksGoingOnline() {
		ConversationAssignmentService assignmentService = mock(ConversationAssignmentService.class);
		when(assignmentService.reassignWaitingPool("tenant-1", 10))
			.thenThrow(new IllegalStateException("assign failed"));
		AgentOnlineEventListener listener = new AgentOnlineEventListener(assignmentService);

		// best-effort：回收异常只记日志，绝不向 setPresence 抛回。
		assertDoesNotThrow(() -> listener.onAgentOnline(new AgentOnlineEvent("tenant-1", "staff-1")));
	}

}
