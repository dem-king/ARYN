package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.config.WechatSubscribeProperties;
import com.aryn.cloud.upms.api.remote.RemoteWechatBindingService;
import com.aryn.cloud.user.api.dto.WechatSubscribeMsgDTO;
import com.aryn.cloud.user.api.remote.RemoteWechatSubscribeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WechatSubscribeChannelServiceImplTest {

	@Test
	void pageComesFromRouteKeyWithoutLeadingSlash() {
		WechatSubscribeProperties properties = new WechatSubscribeProperties();
		properties.setAppId("wx-test");
		properties.setTemplateMap(Map.of("ORDER_DELIVERY_TASK", "tpl-1"));
		RemoteWechatBindingService bindingService = mock(RemoteWechatBindingService.class);
		RemoteWechatSubscribeService subscribeService = mock(RemoteWechatSubscribeService.class);
		when(bindingService.getOpenid(anyString(), anyString())).thenReturn("openid-1");

		WechatSubscribeChannelServiceImpl service = new WechatSubscribeChannelServiceImpl(
				properties, new ObjectMapper(), subscribeService, bindingService);
		MessageSendCommand command = new MessageSendCommand();
		command.setTenantId("t1");
		command.setRecipientId("u1");
		command.setBizType("ORDER_DELIVERY_TASK");
		command.setTitle("配送任务派单通知");
		command.setJumpPayload("{\"route\":\"/pages/delivery/task-detail?id=task-1\"}");

		service.send(command);

		ArgumentCaptor<WechatSubscribeMsgDTO> captor = ArgumentCaptor.forClass(WechatSubscribeMsgDTO.class);
		verify(subscribeService).sendSubscribeMessage(captor.capture());
		// 微信 page 不接受前导斜杠
		assertThat(captor.getValue().getPage()).isEqualTo("pages/delivery/task-detail?id=task-1");
	}

	@Test
	void pageIsNullWhenJumpPayloadHasNoRoute() {
		WechatSubscribeProperties properties = new WechatSubscribeProperties();
		properties.setAppId("wx-test");
		properties.setTemplateMap(Map.of("ORDER_DELIVERY_TASK", "tpl-1"));
		RemoteWechatBindingService bindingService = mock(RemoteWechatBindingService.class);
		RemoteWechatSubscribeService subscribeService = mock(RemoteWechatSubscribeService.class);
		when(bindingService.getOpenid(anyString(), anyString())).thenReturn("openid-1");

		WechatSubscribeChannelServiceImpl service = new WechatSubscribeChannelServiceImpl(
				properties, new ObjectMapper(), subscribeService, bindingService);
		MessageSendCommand command = new MessageSendCommand();
		command.setTenantId("t1");
		command.setRecipientId("u1");
		command.setBizType("ORDER_DELIVERY_TASK");
		command.setTitle("配送任务派单通知");
		// 业务标识型 payload（如 {"bizType":"ORDER","bizId":...}）没有 route，不应跳任何页面
		command.setJumpPayload("{\"bizType\":\"ORDER\",\"bizId\":\"order-1\"}");

		service.send(command);

		ArgumentCaptor<WechatSubscribeMsgDTO> captor = ArgumentCaptor.forClass(WechatSubscribeMsgDTO.class);
		verify(subscribeService).sendSubscribeMessage(captor.capture());
		assertThat(captor.getValue().getPage()).isNull();
	}

}
