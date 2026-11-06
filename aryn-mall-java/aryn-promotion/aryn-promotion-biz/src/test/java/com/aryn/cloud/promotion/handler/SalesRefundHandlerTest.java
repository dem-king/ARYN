package com.aryn.cloud.promotion.handler;

import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.promotion.service.ISalesSettlementService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesRefundHandlerTest {

	@Test
	void skipsWhenOrderIdMissing() {
		ISalesSettlementService settlementService = mock(ISalesSettlementService.class);
		SalesRefundHandler handler = new SalesRefundHandler(settlementService);
		OrderRefundSuccessEvent event = new OrderRefundSuccessEvent();
		event.setRefundNo("refund-1");

		handler.handle(event);

		verify(settlementService, never()).handleRefund(any());
	}

	@Test
	void delegatesRefundToSettlementService() {
		ISalesSettlementService settlementService = mock(ISalesSettlementService.class);
		when(settlementService.handleRefund(any())).thenReturn(Boolean.TRUE);
		SalesRefundHandler handler = new SalesRefundHandler(settlementService);
		OrderRefundSuccessEvent event = new OrderRefundSuccessEvent();
		event.setOrderId("order-1");
		event.setRefundNo("refund-1");
		event.setRefundBaseAmount(new BigDecimal("20.00"));

		handler.handle(event);

		verify(settlementService).handleRefund(event);
	}

}
