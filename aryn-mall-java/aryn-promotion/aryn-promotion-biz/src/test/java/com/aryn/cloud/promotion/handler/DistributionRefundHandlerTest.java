package com.aryn.cloud.promotion.handler;

import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.promotion.service.IDistributionSettlementService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributionRefundHandlerTest {

	@Test
	void settlementFailurePropagatesToMessageListener() {
		IDistributionSettlementService settlementService = mock(IDistributionSettlementService.class);
		when(settlementService.refundCommission(any(), any(), any(), any()))
			.thenThrow(new IllegalStateException("refund failed"));
		DistributionRefundHandler handler = new DistributionRefundHandler(settlementService);
		OrderRefundSuccessEvent event = new OrderRefundSuccessEvent();
		event.setOrderId("order-1");

		assertThatThrownBy(() -> handler.handle(event))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("refund failed");
	}

	@Test
	void forwardsStableRefundNumberAndFreightExcludedBase() throws Exception {
		IDistributionSettlementService settlementService = mock(IDistributionSettlementService.class);
		DistributionRefundHandler handler = new DistributionRefundHandler(settlementService);
		OrderRefundSuccessEvent event = new OrderRefundSuccessEvent();
		event.setOrderId("order-1");
		setRequiredProperty(event, "setRefundNo", String.class, "refund-1");
		setRequiredProperty(event, "setRefundBaseAmount", BigDecimal.class, new BigDecimal("40.00"));

		handler.handle(event);

		verify(settlementService).refundCommission(
			"order-1", "refund-1", null, new BigDecimal("40.00"));
	}

	private void setRequiredProperty(Object target, String methodName, Class<?> parameterType, Object value)
			throws Exception {
		Method method = target.getClass().getMethod(methodName, parameterType);
		method.invoke(target, value);
	}
}
