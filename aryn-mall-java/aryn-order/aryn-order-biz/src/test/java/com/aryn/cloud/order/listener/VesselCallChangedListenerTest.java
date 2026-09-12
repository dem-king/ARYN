package com.aryn.cloud.order.listener;

import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.SharedCartMapper;
import com.aryn.cloud.vessel.api.dto.VesselCallChangedNotice;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 靠港变更提醒消费者契约测试。
 */
class VesselCallChangedListenerTest {

	private OrderInfoMapper orderInfoMapper;

	private SharedCartMapper sharedCartMapper;

	private RocketMQTemplate rocketMQTemplate;

	private VesselCallChangedListener listener;

	@BeforeEach
	void setUp() {
		orderInfoMapper = mock(OrderInfoMapper.class);
		sharedCartMapper = mock(SharedCartMapper.class);
		rocketMQTemplate = mock(RocketMQTemplate.class);
		listener = new VesselCallChangedListener(orderInfoMapper, sharedCartMapper, rocketMQTemplate);
	}

	private VesselCallChangedNotice notice() {
		VesselCallChangedNotice notice = new VesselCallChangedNotice();
		notice.setChangeLogId("log-1");
		notice.setTenantId("tenant-1");
		notice.setCallId("call-1");
		notice.setVesselId("vessel-1");
		notice.setOldEta(LocalDateTime.now().plusDays(1));
		notice.setNewEta(LocalDateTime.now().plusDays(3));
		return notice;
	}

	@Test
	@DisplayName("变更提醒按去重后的用户发送站内信")
	void sendsNoticeToDeduplicatedUsers() {
		OrderInfo order1 = new OrderInfo();
		order1.setUserId("user-1");
		order1.setOrderNo("ON001");
		OrderInfo order2 = new OrderInfo();
		order2.setUserId("user-2");
		order2.setOrderNo("ON002");
		SharedCart cart = new SharedCart();
		cart.setOwnerUserId("user-1");
		cart.setConfirmerUserId("user-3");
		cart.setStatus(SharedCart.STATUS_COLLECTING);
		when(orderInfoMapper.selectList(any())).thenReturn(List.of(order1, order2));
		when(sharedCartMapper.selectList(any())).thenReturn(List.of(cart));

		listener.onMessage(notice());

		ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<MessageSendCommand> payloadCaptor = ArgumentCaptor.forClass(MessageSendCommand.class);
		verify(rocketMQTemplate, org.mockito.Mockito.times(3)).convertAndSend(topicCaptor.capture(),
				payloadCaptor.capture());
		assertEquals(3, payloadCaptor.getAllValues().size());
		MessageSendCommand command = payloadCaptor.getAllValues().get(0);
		assertEquals("call-change:log-1:user-1", command.getEventId());
		assertEquals("MALL_USER", command.getRecipientType());
		assertTrue(command.getContent().contains("ON001"));
	}

	@Test
	@DisplayName("无受影响对象时不发送通知")
	void skipsNoticeWithoutImpact() {
		when(orderInfoMapper.selectList(any())).thenReturn(List.of());
		when(sharedCartMapper.selectList(any())).thenReturn(List.of());

		listener.onMessage(notice());

		verify(rocketMQTemplate, never()).convertAndSend(
				org.mockito.ArgumentMatchers.<String>any(),
				org.mockito.ArgumentMatchers.<MessageSendCommand>any());
	}

	@Test
	@DisplayName("缺少关键字段的消息被忽略")
	void ignoresInvalidNotice() {
		listener.onMessage(new VesselCallChangedNotice());

		verify(orderInfoMapper, never()).selectList(any());
		verify(rocketMQTemplate, never()).convertAndSend(
				org.mockito.ArgumentMatchers.<String>any(),
				org.mockito.ArgumentMatchers.<MessageSendCommand>any());
	}

}
