package com.aryn.cloud.promotion.handler;

import com.aryn.cloud.common.core.entity.OrderPaySuccessEvent;
import com.aryn.cloud.promotion.service.ISalesSettlementService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SalesPayHandlerTest {

	@Test
	void skipsWhenOrderHasNoSalesStaffSnapshot() {
		ISalesSettlementService settlementService = mock(ISalesSettlementService.class);
		SalesPayHandler handler = new SalesPayHandler(settlementService);
		OrderPaySuccessEvent event = new OrderPaySuccessEvent();
		event.setOrderId("order-1");

		handler.handle(event);

		verify(settlementService, never()).handlePaySuccess(any());
	}

	@Test
	void delegatesWhenOrderHasSalesStaffSnapshot() {
		ISalesSettlementService settlementService = mock(ISalesSettlementService.class);
		when(settlementService.handlePaySuccess(any())).thenReturn(Boolean.TRUE);
		SalesPayHandler handler = new SalesPayHandler(settlementService);
		OrderPaySuccessEvent event = new OrderPaySuccessEvent();
		event.setOrderId("order-1");
		event.setSalesStaffId("staff-1");

		handler.handle(event);

		verify(settlementService).handlePaySuccess(event);
	}

}
