package com.aryn.cloud.order.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.pay.api.dto.PayCloseResult;
import com.aryn.cloud.pay.api.remote.RemotePayService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayCancelGateServiceTest {

	private final RemotePayService remotePayService = mock(RemotePayService.class);

	private final PayCancelGateService service = new PayCancelGateService(remotePayService);

	@Test
	void paidOrderCannotBeCancelled() {
		when(remotePayService.closeAndCheckPayOrder("O1"))
			.thenReturn(PayCloseResult.of(PayCloseResult.STATUS_PAID, "SUCCESS", "paid"));

		assertThrows(ArynBusinessException.class, () -> service.assertCancellable(order("1")));
	}

	@Test
	void unknownPaymentBlocksCancellation() {
		when(remotePayService.closeAndCheckPayOrder("O1"))
			.thenReturn(PayCloseResult.of(PayCloseResult.STATUS_UNKNOWN, "USERPAYING", "unknown"));

		assertThrows(ArynBusinessException.class, () -> service.assertCancellable(order("1")));
	}

	@Test
	void closedOrNeverInitiatedPaymentAllowsCancellation() {
		when(remotePayService.closeAndCheckPayOrder("O1"))
			.thenReturn(PayCloseResult.of(PayCloseResult.STATUS_CLOSED, "CLOSED", "closed"));

		assertDoesNotThrow(() -> service.assertCancellable(order("1")));
	}

	@Test
	void noPayTradeOrderAllowsCancellation() {
		when(remotePayService.closeAndCheckPayOrder("O1"))
			.thenReturn(PayCloseResult.of(PayCloseResult.STATUS_NOT_INITIATED, null, "none"));

		assertDoesNotThrow(() -> service.assertCancellable(order("1")));
	}

	@Test
	void cashOnDeliverySkipsOnlinePaymentGate() {
		OrderInfo order = order("3");

		assertDoesNotThrow(() -> service.assertCancellable(order));
		verify(remotePayService, never()).closeAndCheckPayOrder("O1");
	}

	@Test
	void recentCancelLockAlsoBlocksCancellation() {
		OrderInfo order = order("1");
		order.setPaySubmitting("2").setPaySubmitTime(java.time.LocalDateTime.now());

		assertThrows(ArynBusinessException.class, () -> service.assertCancellable(order));
		verify(remotePayService, never()).closeAndCheckPayOrder("O1");
	}

	@Test
	void gateSuspendsCallerTransaction() throws Exception {
		Method method = PayCancelGateService.class.getMethod("assertCancellable", OrderInfo.class);
		Transactional transactional = method.getAnnotation(Transactional.class);
		org.junit.jupiter.api.Assertions.assertNotNull(transactional);
		org.junit.jupiter.api.Assertions.assertEquals(Propagation.NOT_SUPPORTED, transactional.propagation());
	}

	@Test
	void recentPaySubmitBlocksCancellationWithoutReleasingStock() {
		OrderInfo order = order("1");
		order.setPaySubmitting("1").setPaySubmitTime(java.time.LocalDateTime.now());

		assertThrows(ArynBusinessException.class, () -> service.assertCancellable(order));
		verify(remotePayService, never()).closeAndCheckPayOrder("O1");
	}

	@Test
	void stalePaySubmitMarkerDoesNotBlockForever() {
		OrderInfo order = order("1");
		order.setPaySubmitting("1").setPaySubmitTime(java.time.LocalDateTime.now().minusMinutes(10));
		when(remotePayService.closeAndCheckPayOrder("O1"))
			.thenReturn(PayCloseResult.of(PayCloseResult.STATUS_NOT_INITIATED, null, "none"));

		assertDoesNotThrow(() -> service.assertCancellable(order));
	}

	@Test
	void alreadyPaidBusinessOrderCannotBeCancelled() {
		OrderInfo order = order("1");
		order.setPayStatus("1");

		assertThrows(ArynBusinessException.class, () -> service.assertCancellable(order));
		verify(remotePayService, never()).closeAndCheckPayOrder("O1");
	}

	private OrderInfo order(String payType) {
		return new OrderInfo().setOrderNo("O1").setPayStatus("0").setPaymentType(payType);
	}

}
