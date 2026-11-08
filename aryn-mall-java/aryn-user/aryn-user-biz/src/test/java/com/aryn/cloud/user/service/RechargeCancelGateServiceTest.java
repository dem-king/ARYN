package com.aryn.cloud.user.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.dto.PayCloseResult;
import com.aryn.cloud.pay.api.remote.RemotePayService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RechargeCancelGateServiceTest {

	private final RemotePayService remotePayService = mock(RemotePayService.class);

	private final RechargeCancelGateService service = new RechargeCancelGateService(remotePayService);

	@Test
	void paidRechargeCannotBeCancelled() {
		when(remotePayService.closeAndCheckPayOrder("R1"))
			.thenReturn(PayCloseResult.of(PayCloseResult.STATUS_PAID, "SUCCESS", "paid"));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.assertCancellable("R1"));
		assertEquals("充值订单已支付，不能取消", exception.getMsg());
	}

	@Test
	void unknownRechargePaymentBlocksCancel() {
		when(remotePayService.closeAndCheckPayOrder("R1"))
			.thenReturn(PayCloseResult.of(PayCloseResult.STATUS_UNKNOWN, "USERPAYING", "unknown"));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.assertCancellable("R1"));
		assertEquals("支付结果确认中，暂不能取消充值订单", exception.getMsg());
	}

	@Test
	void closedOrNeverInitiatedAllowsCancel() {
		when(remotePayService.closeAndCheckPayOrder("R1"))
			.thenReturn(PayCloseResult.of(PayCloseResult.STATUS_CLOSED, "CLOSED", "closed"));
		assertDoesNotThrow(() -> service.assertCancellable("R1"));

		when(remotePayService.closeAndCheckPayOrder("R2"))
			.thenReturn(PayCloseResult.of(PayCloseResult.STATUS_NOT_INITIATED, null, "none"));
		assertDoesNotThrow(() -> service.assertCancellable("R2"));
	}

	@Test
	void gateSuspendsCallerTransaction() throws Exception {
		Method method = RechargeCancelGateService.class.getMethod("assertCancellable", String.class);
		Transactional transactional = method.getAnnotation(Transactional.class);
		assertNotNull(transactional);
		assertEquals(Propagation.NOT_SUPPORTED, transactional.propagation());
	}

}
