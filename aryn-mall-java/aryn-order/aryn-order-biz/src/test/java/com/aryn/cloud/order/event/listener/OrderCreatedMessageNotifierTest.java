package com.aryn.cloud.order.event.listener;

import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.event.ArynOrderCreateAfterEvent;
import com.aryn.cloud.order.service.OrderStaffNotifyService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderCreatedMessageNotifierTest {

	@Test
	void notifyStaffDelegatesOrderCreatedNotification() {
		OrderStaffNotifyService staffNotifyService = mock(OrderStaffNotifyService.class);
		OrderCreatedMessageNotifier notifier = new OrderCreatedMessageNotifier(staffNotifyService);
		OrderInfo order = new OrderInfo();
		order.setId("order-1");
		order.setOrderNo("NO-1");

		notifier.notifyStaff(new ArynOrderCreateAfterEvent(this, order, List.of(), "2"));

		verify(staffNotifyService).notifyOrderCreated(order);
	}

	@Test
	void notifyStaffSwallowsFailureSoOrderIsNotAffected() {
		OrderStaffNotifyService staffNotifyService = mock(OrderStaffNotifyService.class);
		OrderCreatedMessageNotifier notifier = new OrderCreatedMessageNotifier(staffNotifyService);
		OrderInfo order = new OrderInfo();
		order.setId("order-1");
		doThrow(new IllegalStateException("mq down")).when(staffNotifyService).notifyOrderCreated(order);

		assertThatCode(() -> notifier.notifyStaff(new ArynOrderCreateAfterEvent(this, order, List.of(), "2")))
			.doesNotThrowAnyException();
	}

}
