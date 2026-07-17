package com.aryn.cloud.promotion.handler;

import com.aryn.cloud.common.core.entity.OrderPaySuccessEvent;
import com.aryn.cloud.promotion.api.dto.DistributionSettleDTO;
import com.aryn.cloud.promotion.service.IDistributionSettlementService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributionPayHandlerTest {

	@Test
	void settlementFailurePropagatesToMessageListener() {
		IDistributionSettlementService settlementService = mock(IDistributionSettlementService.class);
		when(settlementService.settleOrder(any())).thenThrow(new IllegalStateException("settlement failed"));
		DistributionPayHandler handler = new DistributionPayHandler(settlementService);
		OrderPaySuccessEvent event = new OrderPaySuccessEvent();
		event.setOrderId("order-1");

		assertThatThrownBy(() -> handler.handle(event))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("settlement failed");
	}

	@Test
	void commissionBaseExcludesFreight() {
		IDistributionSettlementService settlementService = mock(IDistributionSettlementService.class);
		DistributionPayHandler handler = new DistributionPayHandler(settlementService);
		OrderPaySuccessEvent event = new OrderPaySuccessEvent();
		event.setOrderId("order-1");
		event.setUserId("buyer-1");
		event.setPaymentPrice(new BigDecimal("110.00"));
		event.setFreightPrice(new BigDecimal("10.00"));

		handler.handle(event);

		ArgumentCaptor<DistributionSettleDTO> captor = ArgumentCaptor.forClass(DistributionSettleDTO.class);
		verify(settlementService).settleOrder(captor.capture());
		assertThat(captor.getValue().getOrderAmount()).isEqualByComparingTo("100.00");
	}
}
