package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.api.entity.MessageNotice;
import com.aryn.cloud.message.mapper.MessageNoticeMapper;
import com.aryn.cloud.message.mapper.MessageRecipientMapper;
import com.aryn.cloud.message.service.MessagePushService;
import com.aryn.cloud.message.service.WechatSubscribeChannelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

class MessageCommandServiceImplTest {

	@Test
	void duplicateEventCreatesOneNoticeAndOneRecipientPush() {
		MessageNoticeMapper noticeMapper = mock(MessageNoticeMapper.class);
		MessageRecipientMapper recipientMapper = mock(MessageRecipientMapper.class);
		MessagePushService pushService = mock(MessagePushService.class);
		Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
		WechatSubscribeChannelService wechatSubscribeChannelService = mock(WechatSubscribeChannelService.class);
		MessageCommandServiceImpl service = new MessageCommandServiceImpl(noticeMapper, recipientMapper, pushService,
				wechatSubscribeChannelService, new ObjectMapper(), validator);
		AtomicReference<MessageNotice> stored = new AtomicReference<>();
		when(noticeMapper.selectBySource(anyString(), anyString(), anyString())).thenAnswer(invocation -> stored.get());
		when(noticeMapper.insertIgnoreSource(any(MessageNotice.class))).thenAnswer(invocation -> {
			stored.set(invocation.getArgument(0));
			return 1;
		});
		when(recipientMapper.insertIgnoreBatch(any())).thenReturn(1, 0);

		MessageSendCommand command = command();
		service.consume(command);
		service.consume(command);

		verify(noticeMapper, times(1)).insertIgnoreSource(any(MessageNotice.class));
		verify(pushService, times(1)).pushNotice("tenant-1", "MALL_USER", "member-1", stored.get().getId());
		assertThat(stored.get().getCardPayload()).isEqualTo("{\"orderId\":\"order-1\"}");
	}

	private MessageSendCommand command() {
		MessageSendCommand command = new MessageSendCommand();
		command.setEventId("order-paid:order-1");
		command.setTenantId("tenant-1");
		command.setRecipientType("MALL_USER");
		command.setRecipientId("member-1");
		command.setCategory("ORDER");
		command.setTitle("订单支付成功");
		command.setContent("订单已支付");
		command.setBizType("ORDER_PAY");
		command.setBizId("order-1");
		command.setCardPayload("{\"orderId\":\"order-1\"}");
		command.setJumpPayload("{\"bizType\":\"ORDER\",\"bizId\":\"order-1\"}");
		return command;
	}

}
