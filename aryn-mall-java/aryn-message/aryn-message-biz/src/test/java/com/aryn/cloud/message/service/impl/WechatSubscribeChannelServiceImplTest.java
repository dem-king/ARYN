package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.config.WechatSubscribeProperties;
import com.aryn.cloud.upms.api.remote.RemoteWechatBindingService;
import com.aryn.cloud.user.api.dto.WechatSubscribeMsgDTO;
import com.aryn.cloud.user.api.remote.RemoteSocialUserService;
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
				properties, new ObjectMapper(), subscribeService, bindingService,
				mock(RemoteSocialUserService.class));
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
				properties, new ObjectMapper(), subscribeService, bindingService,
				mock(RemoteSocialUserService.class));
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

	@Test
	void mallUserOpenidAndTimeTextAreUsedForSeckillReminder() {
		WechatSubscribeProperties properties = new WechatSubscribeProperties();
		properties.setAppId("wx-test");
		properties.setTemplateMap(Map.of("SECKILL_SESSION_START", "tpl-seckill"));
		RemoteWechatBindingService bindingService = mock(RemoteWechatBindingService.class);
		RemoteWechatSubscribeService subscribeService = mock(RemoteWechatSubscribeService.class);
		RemoteSocialUserService socialUserService = mock(RemoteSocialUserService.class);
		when(socialUserService.getOpenidByMallUserId("t1", "mall-u1")).thenReturn("openid-mall");

		WechatSubscribeChannelServiceImpl service = new WechatSubscribeChannelServiceImpl(
				properties, new ObjectMapper(), subscribeService, bindingService, socialUserService);
		MessageSendCommand command = new MessageSendCommand();
		command.setTenantId("t1");
		command.setRecipientType(MessageIdentityType.MALL_USER.name());
		command.setRecipientId("mall-u1");
		command.setBizType("SECKILL_SESSION_START");
		command.setTitle("秒杀开抢提醒");
		command.setSummary("10:00 场 10:00 开抢");
		command.setTimeText("2026年10月30日 10:00");

		service.send(command);

		ArgumentCaptor<WechatSubscribeMsgDTO> captor = ArgumentCaptor.forClass(WechatSubscribeMsgDTO.class);
		verify(subscribeService).sendSubscribeMessage(captor.capture());
		assertThat(captor.getValue().getOpenid()).isEqualTo("openid-mall");
		assertThat(captor.getValue().getData())
				.anyMatch(item -> "time3".equals(item.get("key"))
						&& "2026年10月30日 10:00".equals(item.get("value")));
		verify(socialUserService).getOpenidByMallUserId("t1", "mall-u1");
	}

}
