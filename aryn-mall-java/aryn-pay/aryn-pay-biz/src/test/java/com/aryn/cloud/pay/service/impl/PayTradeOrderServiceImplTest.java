package com.aryn.cloud.pay.service.impl;

import com.aryn.cloud.pay.PayTestFixtures;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.enums.PayPrepayGateResult;
import com.aryn.cloud.pay.mapper.PayTradeOrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayTradeOrderServiceImplTest {

	private PayTradeOrderMapper mapper;

	private PayTradeOrderServiceImpl service;

	@BeforeEach
	void setUp() {
		mapper = mock(PayTradeOrderMapper.class);
		service = new PayTradeOrderServiceImpl();
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		PayTestFixtures.tenant(PayTestFixtures.TENANT);
	}

	@AfterEach
	void tearDown() {
		PayTestFixtures.clearTenant();
	}

	@Test
	void pendingOrderWinsSubmitGate() {
		when(mapper.markSubmitting(PayTestFixtures.TENANT, "pay-1")).thenReturn(1);

		assertEquals(PayPrepayGateResult.STARTED, service.beginPrepay(order("0", "PENDING")));
	}

	@Test
	void concurrentSubmitIsRejected() {
		when(mapper.markSubmitting(anyString(), anyString())).thenReturn(0);
		when(mapper.selectById("pay-1")).thenReturn(order("0", "SUBMITTING"));

		assertEquals(PayPrepayGateResult.IN_PROGRESS, service.beginPrepay(order("0", "PENDING")));
	}

	@Test
	void closedOrderCannotRestart() {
		when(mapper.markSubmitting(anyString(), anyString())).thenReturn(0);
		when(mapper.selectById("pay-1")).thenReturn(order("0", "CLOSED"));

		assertEquals(PayPrepayGateResult.CLOSED, service.beginPrepay(order("0", "PENDING")));
	}

	@Test
	void paidOrderIsReportedBeforeChannelCall() {
		when(mapper.markSubmitting(anyString(), anyString())).thenReturn(0);
		when(mapper.selectById("pay-1")).thenReturn(order("1", "PAID"));

		assertEquals(PayPrepayGateResult.ALREADY_PAID, service.beginPrepay(order("0", "PENDING")));
	}

	@Test
	void unknownRetryCanSubmitAgain() {
		when(mapper.markSubmitting(anyString(), anyString())).thenReturn(1);

		assertEquals(PayPrepayGateResult.STARTED, service.beginPrepay(order("0", "UNKNOWN")));
	}

	private PayTradeOrder order(String payStatus, String tradeStatus) {
		return new PayTradeOrder().setId("pay-1")
			.setTenantId(PayTestFixtures.TENANT)
			.setOutTradeNo("ORDER-1")
			.setPayStatus(payStatus)
			.setTradeStatus(tradeStatus);
	}

}
