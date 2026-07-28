package com.aryn.cloud.order.listener;

import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.entity.OrderRefund;
import com.aryn.cloud.order.api.dto.OrderConsumerDTO;
import com.aryn.cloud.order.api.enums.OrderArrivalStatusEnum;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.event.listener.OrderPaySuccessNotifier;
import com.aryn.cloud.order.event.ArynOrderPayEvent;
import com.aryn.cloud.order.event.listener.ArynOrderPayEventListener;
import com.aryn.cloud.order.delivery.service.DeliveryTaskCreationService;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.order.service.IOrderItemService;
import com.aryn.cloud.order.service.IOrderRefundService;
import com.aryn.cloud.pay.api.constants.PayConstants;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.messaging.Message;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArynOrderListenerReliabilityTest {

	@BeforeAll
	static void initMybatisMetadata() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderInfo.class);
	}

	@AfterEach
	void clearTenant() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void paidMessageRetryRepublishesNotificationWithoutStateTransition() {
		IOrderInfoService orderService = mock(IOrderInfoService.class);
		IOrderItemService itemService = mock(IOrderItemService.class);
		ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
		OrderPaySuccessNotifier notifier = mock(OrderPaySuccessNotifier.class);
		OrderInfo order = new OrderInfo();
		order.setId("order-1");
		order.setPayStatus(CommonConstants.YES);
		OrderItemEntity item = new OrderItemEntity();
		item.setId("item-1");
		when(orderService.getOne(any(Wrapper.class))).thenReturn(order);
		when(itemService.list(any(Wrapper.class))).thenReturn(List.of(item));
		ArynPayListener listener = new ArynPayListener(orderService, itemService, publisher, notifier);

		listener.onMessage(payMessage());

		verify(notifier).notify(order, List.of(item));
		verify(publisher, never()).publishEvent(any());
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void payRepublishFailurePropagatesAndClearsTenant() {
		IOrderInfoService orderService = mock(IOrderInfoService.class);
		IOrderItemService itemService = mock(IOrderItemService.class);
		OrderPaySuccessNotifier notifier = mock(OrderPaySuccessNotifier.class);
		OrderInfo order = new OrderInfo();
		order.setId("order-1");
		order.setPayStatus(CommonConstants.YES);
		when(orderService.getOne(any(Wrapper.class))).thenReturn(order);
		when(itemService.list(any(Wrapper.class))).thenReturn(List.of(new OrderItemEntity()));
		org.mockito.Mockito.doThrow(new IllegalStateException("send failed"))
			.when(notifier).notify(any(), any());
		ArynPayListener listener = new ArynPayListener(
			orderService, itemService, mock(ApplicationEventPublisher.class), notifier);

		assertThatThrownBy(() -> listener.onMessage(payMessage()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("send failed");
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void refundSuccessRetryRepublishesAndClearsTenant() {
		IOrderRefundService refundService = mock(IOrderRefundService.class);
		IOrderItemService itemService = mock(IOrderItemService.class);
		IOrderInfoService orderService = mock(IOrderInfoService.class);
		RocketMQTemplate template = mock(RocketMQTemplate.class);
		OrderRefund refund = new OrderRefund();
		refund.setId("refund-1");
		refund.setOrderId("order-1");
		refund.setOrderItemId("item-1");
		refund.setArrivalStatus(OrderArrivalStatusEnum.REFUND_SUCCESS.getCode());
		when(refundService.getOne(any(Wrapper.class))).thenReturn(refund);
		when(itemService.getById("item-1")).thenReturn(new OrderItemEntity());
		when(itemService.updateById(any())).thenReturn(true);
		when(itemService.list(any(Wrapper.class))).thenReturn(List.of(new OrderItemEntity()));
		when(orderService.getById("order-1")).thenReturn(new OrderInfo());
		ArynRefundListener listener = new ArynRefundListener(refundService, itemService, orderService, template);

		listener.onMessage(refundMessage());

		verify(template).syncSend(any(String.class), any(Message.class), anyLong());
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void refundEarlyReturnAlsoClearsTenant() {
		ArynRefundListener listener = new ArynRefundListener(
			mock(IOrderRefundService.class), mock(IOrderItemService.class),
			mock(IOrderInfoService.class), mock(RocketMQTemplate.class));
		JSONObject message = new JSONObject();
		message.put(PayConstants.TENANT_ID, "tenant-1");

		listener.onMessage(message.toJSONString());

		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void payMessageWithoutTenantClearsStaleContext() {
		ArynTenantContextHolder.setTenantId("stale-tenant");
		ArynPayListener listener = new ArynPayListener(
			mock(IOrderInfoService.class), mock(IOrderItemService.class),
			mock(ApplicationEventPublisher.class), mock(OrderPaySuccessNotifier.class));

		listener.onMessage("{}");

		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void refundMessageWithoutTenantClearsStaleContext() {
		ArynTenantContextHolder.setTenantId("stale-tenant");
		ArynRefundListener listener = new ArynRefundListener(
			mock(IOrderRefundService.class), mock(IOrderItemService.class),
			mock(IOrderInfoService.class), mock(RocketMQTemplate.class));

		listener.onMessage("{}");

		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void duplicateCancelMessageDoesNotCancelAnAlreadyCanceledOrder() {
		IOrderInfoService orderService = mock(IOrderInfoService.class);
		OrderInfo order = new OrderInfo();
		order.setId("order-1");
		order.setPayStatus(CommonConstants.NO);
		order.setStatus(OrderStatusEnum.CANCELED.getCode());
		when(orderService.getById("order-1")).thenReturn(order);
		OrderCancelListener listener = new OrderCancelListener(orderService);
		OrderConsumerDTO message = new OrderConsumerDTO();
		message.setOrderId("order-1");
		message.setTenantId("tenant-1");

		listener.onMessage(message);

		verify(orderService, never()).cancelOrder(any());
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void payStateWriteFailureDoesNotPublishSuccessNotification() {
		IOrderInfoService orderService = mock(IOrderInfoService.class);
		IOrderItemService itemService = mock(IOrderItemService.class);
		OrderPaySuccessNotifier notifier = mock(OrderPaySuccessNotifier.class);
		OrderInfo order = new OrderInfo();
		order.setId("order-1");
		order.setDeliveryWay("1");
		when(orderService.updateById(order)).thenReturn(false);
		ArynOrderPayEventListener listener = new ArynOrderPayEventListener(
			orderService, itemService, mock(DeliveryTaskCreationService.class), notifier);

		assertThatThrownBy(() -> listener.hxPayEventListener(
			new ArynOrderPayEvent(this, order, List.of(new OrderItemEntity()))))
			.isInstanceOf(ArynBusinessException.class);

		verify(notifier, never()).notify(any(), any());
	}

	@Test
	void deliveryTaskCreationFailureDoesNotPublishSuccessNotification() {
		IOrderInfoService orderService = mock(IOrderInfoService.class);
		IOrderItemService itemService = mock(IOrderItemService.class);
		DeliveryTaskCreationService creationService = mock(DeliveryTaskCreationService.class);
		OrderPaySuccessNotifier notifier = mock(OrderPaySuccessNotifier.class);
		OrderInfo order = new OrderInfo();
		order.setId("order-1");
		order.setDeliveryWay("3");
		OrderItemEntity item = new OrderItemEntity().setId("item-1");
		when(orderService.update(any(Wrapper.class))).thenReturn(true);
		when(itemService.updateBatchById(List.of(item))).thenReturn(true);
		when(creationService.createIfNeeded(order, List.of(item)))
			.thenThrow(new IllegalStateException("task insert failed"));
		ArynOrderPayEventListener listener = new ArynOrderPayEventListener(
			orderService, itemService, creationService, notifier);

		assertThatThrownBy(() -> listener.hxPayEventListener(
			new ArynOrderPayEvent(this, order, List.of(item))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("task insert failed");

		verify(notifier, never()).notify(any(), any());
	}

	@Test
	void refundStateWriteFailureDoesNotPublishSuccessNotification() {
		IOrderRefundService refundService = mock(IOrderRefundService.class);
		IOrderItemService itemService = mock(IOrderItemService.class);
		IOrderInfoService orderService = mock(IOrderInfoService.class);
		RocketMQTemplate template = mock(RocketMQTemplate.class);
		OrderRefund refund = refund();
		when(refundService.getOne(any(Wrapper.class))).thenReturn(refund);
		when(refundService.updateById(refund)).thenReturn(false);
		when(itemService.getById("item-1")).thenReturn(new OrderItemEntity());
		when(orderService.getById("order-1")).thenReturn(new OrderInfo());
		ArynRefundListener listener = new ArynRefundListener(refundService, itemService, orderService, template);

		assertThatThrownBy(() -> listener.onMessage(refundMessage()))
			.isInstanceOf(ArynBusinessException.class);

		verify(template, never()).syncSend(any(String.class), any(Message.class), anyLong());
	}

	@Test
	void refundSuccessNotificationWaitsForTransactionCommit() {
		IOrderRefundService refundService = mock(IOrderRefundService.class);
		IOrderItemService itemService = mock(IOrderItemService.class);
		IOrderInfoService orderService = mock(IOrderInfoService.class);
		RocketMQTemplate template = mock(RocketMQTemplate.class);
		OrderRefund refund = refund();
		when(refundService.getOne(any(Wrapper.class))).thenReturn(refund);
		when(refundService.updateById(refund)).thenReturn(true);
		when(itemService.getById("item-1")).thenReturn(new OrderItemEntity());
		when(itemService.updateById(any())).thenReturn(true);
		when(itemService.list(any(Wrapper.class))).thenReturn(List.of(new OrderItemEntity()));
		when(orderService.getById("order-1")).thenReturn(new OrderInfo());
		ArynRefundListener listener = new ArynRefundListener(refundService, itemService, orderService, template);

		TransactionSynchronizationManager.initSynchronization();
		try {
			listener.onMessage(refundMessage());
			verify(template, never()).syncSend(any(String.class), any(Message.class), anyLong());
			TransactionSynchronizationManager.getSynchronizations().forEach(synchronization -> synchronization.afterCommit());
			verify(template).syncSend(any(String.class), any(Message.class), anyLong());
		}
		finally {
			TransactionSynchronizationManager.clearSynchronization();
		}
	}

	private String payMessage() {
		JSONObject extra = new JSONObject();
		extra.put(PayConstants.EXTRA_PARAMS_PAY_TYPE, PayConstants.PAY_TYPE_1);
		JSONObject message = new JSONObject();
		message.put(PayConstants.TENANT_ID, "tenant-1");
		message.put(PayConstants.OUT_TRADE_NO, "ORDER-1");
		message.put(PayConstants.PAY_SUCCESS_TIME, LocalDateTime.now());
		message.put(PayConstants.EXTRA_PARAMS, extra.toJSONString());
		message.put(PayConstants.CHANNEL_ORDER_NO, "TX-1");
		return message.toJSONString();
	}

	private String refundMessage() {
		JSONObject message = new JSONObject();
		message.put(PayConstants.TENANT_ID, "tenant-1");
		message.put(PayConstants.REFUND_TRADE_NO, "REFUND-TX-1");
		return message.toJSONString();
	}

	private OrderRefund refund() {
		OrderRefund refund = new OrderRefund();
		refund.setId("refund-1");
		refund.setOrderId("order-1");
		refund.setOrderItemId("item-1");
		refund.setArrivalStatus(OrderArrivalStatusEnum.REFUNDING.getCode());
		return refund;
	}
}
