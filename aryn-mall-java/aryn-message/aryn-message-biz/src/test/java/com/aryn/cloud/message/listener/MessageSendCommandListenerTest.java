package com.aryn.cloud.message.listener;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.service.MessageCommandService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class MessageSendCommandListenerTest {

	@AfterEach
	void clearTenant() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void clearsStaleTenantAfterSuccessfulConsumption() {
		MessageCommandService service = mock(MessageCommandService.class);
		MessageSendCommandListener listener = new MessageSendCommandListener(service);
		ArynTenantContextHolder.setTenantId("stale-tenant");

		listener.onMessage(command());

		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void clearsTenantWhenConsumptionFails() {
		MessageCommandService service = mock(MessageCommandService.class);
		MessageSendCommand command = command();
		doThrow(new IllegalStateException("boom")).when(service).consume(command);
		MessageSendCommandListener listener = new MessageSendCommandListener(service);

		assertThatThrownBy(() -> listener.onMessage(command)).isInstanceOf(IllegalStateException.class);
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	private MessageSendCommand command() {
		MessageSendCommand command = new MessageSendCommand();
		command.setTenantId("tenant-1");
		return command;
	}

}
