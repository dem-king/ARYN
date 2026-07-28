package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.api.entity.MessageChannelTask;
import com.aryn.cloud.message.mapper.MessageChannelTaskMapper;
import com.aryn.cloud.message.service.impl.WechatSubscribeChannelService;
import com.aryn.cloud.upms.api.remote.RemoteStaffWechatBindingService;
import com.aryn.cloud.user.api.dto.MiniAppSubscribeResult;
import com.aryn.cloud.user.api.remote.RemoteMiniAppGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatSubscribeChannelServiceTest {

	@Test
	void unboundStaffIsTerminalAndDoesNotRetry() {
		Fixture fixture = fixture();
		MessageChannelTask task = task();
		when(fixture.bindingService.getBoundOpenId("tenant-1", "staff-1", "delivery-app"))
			.thenReturn(null);

		fixture.wechatService.dispatch(task);

		verify(fixture.mapper).markTerminal(eq("tenant-1"), eq("channel-1"),
			eq("员工未绑定配送微信"), any(LocalDateTime.class));
		verify(fixture.gateway, never()).sendSubscribeMessage(any(), any(), any(), any(), any());
	}

	@Test
	void refusalIsTerminalButRateLimitUsesBackoffRetry() {
		Fixture fixture = fixture();
		when(fixture.bindingService.getBoundOpenId("tenant-1", "staff-1", "delivery-app"))
			.thenReturn("openid-1");
		when(fixture.gateway.sendSubscribeMessage(eq("delivery-app"), eq("openid-1"), eq("template-1"),
			any(), eq("pages/tasks/detail?id=task-1")))
			.thenReturn(MiniAppSubscribeResult.terminal("43101", "用户拒绝订阅"),
				MiniAppSubscribeResult.retryable("45009", "接口限流"));

		fixture.wechatService.dispatch(task());
		MessageChannelTask retryTask = task();
		retryTask.setRetryCount(2);
		fixture.wechatService.dispatch(retryTask);

		verify(fixture.mapper).markTerminal(eq("tenant-1"), eq("channel-1"),
			eq("43101:用户拒绝订阅"), any(LocalDateTime.class));
		verify(fixture.mapper).markRetry(eq("tenant-1"), eq("channel-1"), eq(3),
			any(LocalDateTime.class), eq("45009:接口限流"), any(LocalDateTime.class));
	}

	@Test
	void channelCreationIsIdempotentAndUsesServerWhitelist() throws Exception {
		Fixture fixture = fixture();
		when(fixture.mapper.insertIgnore(any(MessageChannelTask.class))).thenReturn(1, 0);
		MessageChannelService channelService = new MessageChannelService(fixture.mapper, fixture.wechatService,
			new ObjectMapper());
		MessageSendCommand command = command();

		channelService.createAndDispatch(command, "message-1");
		channelService.createAndDispatch(command, "message-1");

		var captor = org.mockito.ArgumentCaptor.forClass(MessageChannelTask.class);
		verify(fixture.mapper, org.mockito.Mockito.times(2)).insertIgnore(captor.capture());
		verify(fixture.wechatService, org.mockito.Mockito.times(1)).dispatch(any(MessageChannelTask.class));
		String templateParams = captor.getAllValues().get(0).getTemplateParams();
		assertThat(templateParams).contains("delivery-app", "thing1", "task-1")
			.doesNotContain("evilField", "https://evil.example");
	}

	private Fixture fixture() {
		MessageChannelTaskMapper mapper = mock(MessageChannelTaskMapper.class);
		when(mapper.markSending(any(), any(), any(LocalDateTime.class))).thenReturn(1);
		RemoteStaffWechatBindingService bindingService = mock(RemoteStaffWechatBindingService.class);
		RemoteMiniAppGateway gateway = mock(RemoteMiniAppGateway.class);
		WechatSubscribeChannelService wechatService = mock(WechatSubscribeChannelService.class,
			org.mockito.Mockito.withSettings().useConstructor(mapper, bindingService, gateway, new ObjectMapper())
				.defaultAnswer(org.mockito.Mockito.CALLS_REAL_METHODS));
		return new Fixture(mapper, bindingService, gateway, wechatService);
	}

	private MessageChannelTask task() {
		MessageChannelTask task = new MessageChannelTask();
		task.setId("channel-1");
		task.setTenantId("tenant-1");
		task.setRecipientId("staff-1");
		task.setTemplateCode("template-1");
		task.setTemplateParams("{\"appId\":\"delivery-app\",\"page\":\"pages/tasks/detail?id=task-1\","
			+ "\"data\":{\"thing1\":\"新配送任务\"}}");
		task.setRetryCount(0);
		return task;
	}

	private MessageSendCommand command() {
		MessageSendCommand command = new MessageSendCommand();
		command.setEventId("delivery-assigned:task-1:2");
		command.setTenantId("tenant-1");
		command.setRecipientType("SYS_USER");
		command.setRecipientId("staff-1");
		command.setCategory("DELIVERY");
		command.setTitle("新的商城配送任务");
		command.setSummary("订单 ORDER-1 等待配送");
		command.setContent("请及时处理商城配送任务");
		command.setBizType("DELIVERY_ASSIGNMENT");
		command.setBizId("task-1");
		command.setChannels(Set.of("IN_APP", "WECHAT_SUBSCRIBE"));
		command.setMiniAppId("delivery-app");
		command.setTemplateCode("template-1");
		command.setCardPayload("{\"evilField\":\"secret\"}");
		command.setJumpPayload("{\"url\":\"https://evil.example\"}");
		return command;
	}

	private record Fixture(MessageChannelTaskMapper mapper, RemoteStaffWechatBindingService bindingService,
			RemoteMiniAppGateway gateway, WechatSubscribeChannelService wechatService) {
	}

}
