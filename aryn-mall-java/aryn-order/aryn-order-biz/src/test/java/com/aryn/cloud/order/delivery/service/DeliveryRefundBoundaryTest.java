package com.aryn.cloud.order.delivery.service;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTask;
import com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum;
import com.aryn.cloud.order.api.entity.OrderConfig;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.entity.OrderRefund;
import com.aryn.cloud.order.api.enums.OrderArrivalStatusEnum;
import com.aryn.cloud.order.api.enums.OrderItemStatusEnum;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskMapper;
import com.aryn.cloud.order.listener.ArynRefundListener;
import com.aryn.cloud.order.mapper.OrderDeliveryMapper;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import com.aryn.cloud.order.mapper.OrderRefundMapper;
import com.aryn.cloud.order.service.IOrderConfigService;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.order.service.IOrderItemService;
import com.aryn.cloud.order.service.IOrderRefundService;
import com.aryn.cloud.order.service.impl.OrderRefundServiceImpl;
import com.aryn.cloud.pay.api.constants.PayConstants;
import com.aryn.cloud.pay.api.remote.RemoteRefundService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryRefundBoundaryTest {

	@BeforeAll
	static void initMybatisMetadata() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OrderInfo.class);
	}

	@Test
	void refundRequestedAfterPickupAtomicallyMarksWholeTaskReturnPending() {
		OrderDeliveryTaskMapper mapper = mock(OrderDeliveryTaskMapper.class);
		when(mapper.selectByTenantAndOrderId("tenant-1", "order-1"))
			.thenReturn(task(DeliveryTaskStatusEnum.DELIVERING, LocalDateTime.now(), null));
		when(mapper.markReturnPendingForRefund(eq("tenant-1"), eq("order-1"), any())).thenReturn(1);
		DeliveryRefundBoundaryService service = new DeliveryRefundBoundaryService(mapper);

		service.onRefundRequested(order());

		verify(mapper).markReturnPendingForRefund(eq("tenant-1"), eq("order-1"), any());
	}

	@Test
	void refundReleaseRequiresConfirmedReturnAfterPickup() {
		OrderDeliveryTaskMapper mapper = mock(OrderDeliveryTaskMapper.class);
		DeliveryRefundBoundaryService service = new DeliveryRefundBoundaryService(mapper);
		when(mapper.selectByTenantAndOrderId("tenant-1", "order-1"))
			.thenReturn(task(DeliveryTaskStatusEnum.RETURN_PENDING, LocalDateTime.now(), null));

		assertThatThrownBy(() -> service.requireRefundRelease(order()))
			.isInstanceOf(ArynBusinessException.class);

		when(mapper.selectByTenantAndOrderId("tenant-1", "order-1"))
			.thenReturn(task(DeliveryTaskStatusEnum.CLOSED, LocalDateTime.now(), LocalDateTime.now()));
		assertThatCode(() -> service.requireRefundRelease(order())).doesNotThrowAnyException();
	}

	@Test
	void mallDeliveryReceiptRequiresDeliveredTask() {
		OrderDeliveryTaskMapper mapper = mock(OrderDeliveryTaskMapper.class);
		DeliveryRefundBoundaryService service = new DeliveryRefundBoundaryService(mapper);
		when(mapper.selectByTenantAndOrderId("tenant-1", "order-1"))
			.thenReturn(task(DeliveryTaskStatusEnum.DELIVERING, LocalDateTime.now(), null));

		assertThatThrownBy(() -> service.requireDeliveredForReceipt(order()))
			.isInstanceOf(ArynBusinessException.class);

		when(mapper.selectByTenantAndOrderId("tenant-1", "order-1"))
			.thenReturn(task(DeliveryTaskStatusEnum.DELIVERED, LocalDateTime.now(), null));
		assertThatCode(() -> service.requireDeliveredForReceipt(order())).doesNotThrowAnyException();
	}

	@Test
	void fullRefundBeforePickupClosesTaskIdempotently() {
		OrderDeliveryTaskMapper mapper = mock(OrderDeliveryTaskMapper.class);
		DeliveryRefundBoundaryService service = new DeliveryRefundBoundaryService(mapper);

		service.closeBeforePickupAfterFullRefund(order());

		verify(mapper).closeBeforePickupAfterFullRefund(eq("tenant-1"), eq("order-1"), any());
	}

	@Test
	void refundApplicationInvokesDeliveryBoundaryInsideServicePath() {
		OrderInfoMapper orderInfoMapper = mock(OrderInfoMapper.class);
		OrderItemMapper itemMapper = mock(OrderItemMapper.class);
		OrderRefundMapper refundMapper = mock(OrderRefundMapper.class);
		DeliveryRefundBoundaryService boundaryService = mock(DeliveryRefundBoundaryService.class);
		OrderItemEntity item = new OrderItemEntity().setId("item-1")
			.setOrderId("order-1")
			.setStatus(OrderItemStatusEnum.SHIPPED.getCode())
			.setPaymentPrice(new BigDecimal("10.00"));
		OrderInfo ownerOrder = order();
		when(itemMapper.selectById("item-1")).thenReturn(item);
		when(orderInfoMapper.selectOne(any(Wrapper.class))).thenReturn(ownerOrder);
		when(refundMapper.insert(any(OrderRefund.class))).thenReturn(1);
		OrderRefundServiceImpl service = refundService(orderInfoMapper, itemMapper, refundMapper, boundaryService);
		OrderRefund request = new OrderRefund().setOrderItemId("item-1").setUserId("user-1");

		service.saveRefund(request);

		verify(boundaryService).onRefundRequested(ownerOrder);
	}

	@Test
	void refundApprovalDoesNotCallPaymentBeforeReturnConfirmation() {
		OrderInfoMapper orderInfoMapper = mock(OrderInfoMapper.class);
		OrderItemMapper itemMapper = mock(OrderItemMapper.class);
		OrderRefundMapper refundMapper = mock(OrderRefundMapper.class);
		RemoteRefundService remoteRefundService = mock(RemoteRefundService.class);
		DeliveryRefundBoundaryService boundaryService = mock(DeliveryRefundBoundaryService.class);
		OrderRefund storedRefund = new OrderRefund().setId("refund-1")
			.setOrderId("order-1")
			.setOrderItemId("item-1");
		OrderItemEntity item = new OrderItemEntity().setId("item-1")
			.setStatus(OrderItemStatusEnum.AFTER_SALE_PROCESSING.getCode());
		OrderInfo order = order().setPayStatus(CommonConstants.YES);
		when(refundMapper.selectById("refund-1")).thenReturn(storedRefund);
		when(itemMapper.selectById("item-1")).thenReturn(item);
		when(orderInfoMapper.selectById("order-1")).thenReturn(order);
		org.mockito.Mockito.doThrow(new ArynBusinessException("尚未退回"))
			.when(boundaryService).requireRefundRelease(order);
		OrderRefundServiceImpl service = refundService(orderInfoMapper, itemMapper, refundMapper,
			remoteRefundService, boundaryService);
		OrderRefund request = new OrderRefund().setId("refund-1")
			.setOperateStatus(MallOrderConstants.OPERATE_STATUS_REFUND);

		assertThatThrownBy(() -> service.refund(request)).isInstanceOf(ArynBusinessException.class);

		verify(boundaryService).requireRefundRelease(order);
		verify(remoteRefundService, never()).refunds(any());
	}

	@Test
	void duplicateRefundCallbackDoesNotCloseTaskTwice() {
		IOrderRefundService refundService = mock(IOrderRefundService.class);
		IOrderItemService itemService = mock(IOrderItemService.class);
		IOrderInfoService orderService = mock(IOrderInfoService.class);
		DeliveryRefundBoundaryService boundaryService = mock(DeliveryRefundBoundaryService.class);
		OrderRefund refund = new OrderRefund().setId("refund-1")
			.setOrderId("order-1")
			.setOrderItemId("item-1")
			.setArrivalStatus(OrderArrivalStatusEnum.REFUND_SUCCESS.getCode());
		OrderItemEntity item = new OrderItemEntity().setId("item-1")
			.setStatus(OrderItemStatusEnum.REFUNDED.getCode());
		OrderInfo activeOrder = order().setStatus(OrderStatusEnum.WAITING_FOR_DELIVERY.getCode());
		OrderInfo canceledOrder = order().setStatus(OrderStatusEnum.CANCELED.getCode());
		when(refundService.getOne(any(Wrapper.class))).thenReturn(refund);
		when(itemService.getById("item-1")).thenReturn(item);
		when(itemService.list(any(Wrapper.class))).thenReturn(List.of());
		when(orderService.getById("order-1")).thenReturn(activeOrder, canceledOrder);
		when(orderService.updateById(any(OrderInfo.class))).thenReturn(true);
		ArynRefundListener listener = new ArynRefundListener(refundService, itemService, orderService,
			mock(RocketMQTemplate.class), boundaryService);

		listener.onMessage(refundMessage());
		listener.onMessage(refundMessage());

		verify(boundaryService, times(1)).closeBeforePickupAfterFullRefund(activeOrder);
	}

	private OrderInfo order() {
		return new OrderInfo().setId("order-1")
			.setTenantId("tenant-1")
			.setDeliveryWay(MallOrderConstants.DELIVERY_WAY_3);
	}

	private OrderRefundServiceImpl refundService(OrderInfoMapper orderInfoMapper, OrderItemMapper itemMapper,
			OrderRefundMapper refundMapper, DeliveryRefundBoundaryService boundaryService) {
		return refundService(orderInfoMapper, itemMapper, refundMapper, mock(RemoteRefundService.class),
			boundaryService);
	}

	private OrderRefundServiceImpl refundService(OrderInfoMapper orderInfoMapper, OrderItemMapper itemMapper,
			OrderRefundMapper refundMapper, RemoteRefundService remoteRefundService,
			DeliveryRefundBoundaryService boundaryService) {
		IOrderConfigService configService = mock(IOrderConfigService.class);
		when(configService.getConfig()).thenReturn(new OrderConfig());
		return new TestOrderRefundService(orderInfoMapper, itemMapper, remoteRefundService,
			configService, mock(OrderDeliveryMapper.class), boundaryService, refundMapper);
	}

	private String refundMessage() {
		JSONObject message = new JSONObject();
		message.put(PayConstants.TENANT_ID, "tenant-1");
		message.put(PayConstants.REFUND_TRADE_NO, "refund-trade-1");
		return message.toJSONString();
	}

	private OrderDeliveryTask task(DeliveryTaskStatusEnum status, LocalDateTime pickedUpAt,
			LocalDateTime returnedAt) {
		return new OrderDeliveryTask().setId("task-1")
			.setOrderId("order-1")
			.setTenantId("tenant-1")
			.setStatus(status.name())
			.setPickedUpAt(pickedUpAt)
			.setReturnedAt(returnedAt);
	}

	private static final class TestOrderRefundService extends OrderRefundServiceImpl {

		private TestOrderRefundService(OrderInfoMapper orderInfoMapper, OrderItemMapper orderItemMapper,
				RemoteRefundService remoteRefundService, IOrderConfigService orderConfigService,
				OrderDeliveryMapper orderDeliveryMapper, DeliveryRefundBoundaryService boundaryService,
				OrderRefundMapper refundMapper) {
			super(orderInfoMapper, orderItemMapper, remoteRefundService, orderConfigService, orderDeliveryMapper,
				boundaryService);
			this.baseMapper = refundMapper;
		}
	}

}
