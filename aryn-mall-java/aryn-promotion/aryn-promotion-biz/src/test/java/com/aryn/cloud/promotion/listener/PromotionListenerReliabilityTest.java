package com.aryn.cloud.promotion.listener;

import com.aryn.cloud.common.core.entity.OrderPaySuccessEvent;
import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.handler.PromotionPayEventHandler;
import com.aryn.cloud.promotion.handler.PromotionRefundEventHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PromotionListenerReliabilityTest {

	@AfterEach
	void clearTenantContext() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void payFailurePropagatesAndClearsTenantContext() {
		PromotionPayEventHandler failingHandler = event -> {
			throw new IllegalStateException("pay failed");
		};
		PromotionPayListener listener = new PromotionPayListener(List.of(failingHandler));
		OrderPaySuccessEvent event = new OrderPaySuccessEvent();
		event.setTenantId("tenant-1");

		assertThatThrownBy(() -> listener.onMessage(event))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("pay failed");
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void refundFailurePropagatesAndClearsTenantContext() {
		PromotionRefundEventHandler failingHandler = event -> {
			throw new IllegalStateException("refund failed");
		};
		PromotionRefundListener listener = new PromotionRefundListener(List.of(failingHandler));
		OrderRefundSuccessEvent event = new OrderRefundSuccessEvent();
		event.setTenantId("tenant-1");

		assertThatThrownBy(() -> listener.onMessage(event))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("refund failed");
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void successfulMessageAlsoClearsTenantContext() {
		PromotionPayListener listener = new PromotionPayListener(List.of(event -> {
		}));
		OrderPaySuccessEvent event = new OrderPaySuccessEvent();
		event.setTenantId("tenant-1");

		listener.onMessage(event);

		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}
}
