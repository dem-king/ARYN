package com.aryn.cloud.pay.service;

import com.aryn.cloud.pay.api.entity.PayEventOutbox;
import com.aryn.cloud.pay.mapper.PayEventOutboxMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayEventOutboxServiceTest {

	private PayEventOutboxMapper outboxMapper;

	private PayEventOutboxTxService txService;

	private RocketMQTemplate rocketMQTemplate;

	private PayEventOutboxService service;

	@BeforeEach
	void setUp() {
		outboxMapper = mock(PayEventOutboxMapper.class);
		txService = mock(PayEventOutboxTxService.class);
		rocketMQTemplate = mock(RocketMQTemplate.class);
		service = new PayEventOutboxService(outboxMapper, txService, rocketMQTemplate);
		ReflectionTestUtils.setField(service, "batchSize", 100);
		ReflectionTestUtils.setField(service, "maxAttempts", 20);
		ReflectionTestUtils.setField(service, "staleSendingSeconds", 120L);
	}

	@Test
	void enqueuePersistsPendingEventWithUniqueEventId() {
		service.enqueue("tenant-1", "PAY_SUCCESS", "ORDER-1", "pay-topic", "{\"outTradeNo\":\"ORDER-1\"}");

		ArgumentCaptor<PayEventOutbox> captor = ArgumentCaptor.forClass(PayEventOutbox.class);
		verify(outboxMapper).insert(captor.capture());
		PayEventOutbox event = captor.getValue();
		assertNotNull(event.getEventId());
		assertEquals("tenant-1", event.getTenantId());
		assertEquals("0", event.getStatus());
		assertEquals("ORDER-1", event.getBusinessKey());
	}

	@Test
	void relayClaimsThenSendsOutsideTransactionThenMarksSent() {
		PayEventOutbox event = dueEvent("outbox-1");
		when(outboxMapper.selectDue(any(LocalDateTime.class), anyInt())).thenReturn(List.of(event));
		when(txService.claim("outbox-1")).thenReturn(true);
		doAnswer(invocation -> {
			// 渠道/MQ 发送期间不得有活动数据库事务
			assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
			return null;
		}).when(rocketMQTemplate).syncSend(eq("pay-topic"), any(Message.class), anyLong());

		int sent = service.relayBatch();

		assertEquals(1, sent);
		verify(txService).claim("outbox-1");
		verify(rocketMQTemplate).syncSend(eq("pay-topic"), any(Message.class), anyLong());
		verify(txService).markSent("outbox-1");
	}

	@Test
	void sendFailureSchedulesRetryWithoutMarkingSent() {
		PayEventOutbox event = dueEvent("outbox-2");
		when(outboxMapper.selectDue(any(LocalDateTime.class), anyInt())).thenReturn(List.of(event));
		when(txService.claim("outbox-2")).thenReturn(true);
		when(rocketMQTemplate.syncSend(anyString(), any(Message.class), anyLong()))
			.thenThrow(new IllegalStateException("mq down"));

		int sent = service.relayBatch();

		assertEquals(0, sent);
		verify(txService, never()).markSent(anyString());
		verify(txService).markRetry(eq("outbox-2"), eq("0"), any(LocalDateTime.class), anyString());
	}

	@Test
	void lostClaimDoesNotSend() {
		when(outboxMapper.selectDue(any(LocalDateTime.class), anyInt())).thenReturn(List.of(dueEvent("outbox-3")));
		when(txService.claim("outbox-3")).thenReturn(false);

		assertEquals(0, service.relayBatch());
		verify(rocketMQTemplate, never()).syncSend(anyString(), any(Message.class), anyLong());
	}

	@Test
	void crashAfterSendBeforeMarkLeavesEventForAtLeastOnceRedelivery() {
		PayEventOutbox event = dueEvent("outbox-4");
		when(outboxMapper.selectDue(any(LocalDateTime.class), anyInt())).thenReturn(List.of(event));
		when(txService.claim("outbox-4")).thenReturn(true);
		doAnswer(invocation -> null).when(rocketMQTemplate)
			.syncSend(eq("pay-topic"), any(Message.class), anyLong());
		doAnswer(invocation -> {
			throw new IllegalStateException("db down after send");
		}).when(txService).markSent("outbox-4");

		int sent = service.relayBatch();

		assertEquals(0, sent, "标记失败不能宣称已投递");
		verify(txService).markSent("outbox-4");
		// 事件保持投递中；下一轮 resetStaleSending 后会重新 claim/发送，形成 at-least-once
		when(txService.claim("outbox-4")).thenReturn(true);
		service.relayBatch();
		verify(rocketMQTemplate, org.mockito.Mockito.times(2))
			.syncSend(eq("pay-topic"), any(Message.class), anyLong());
		verify(txService, org.mockito.Mockito.times(2)).resetStaleSending(any(LocalDateTime.class));
	}

	private PayEventOutbox dueEvent(String id) {
		return new PayEventOutbox().setId(id)
			.setEventId(id)
			.setTenantId("tenant-1")
			.setEventType("PAY_SUCCESS")
			.setBusinessKey("ORDER-1")
			.setTopic("pay-topic")
			.setPayload("{}")
			.setStatus("0")
			.setAttempts(0)
			.setNextRetryTime(LocalDateTime.now().minusSeconds(1));
	}

}
