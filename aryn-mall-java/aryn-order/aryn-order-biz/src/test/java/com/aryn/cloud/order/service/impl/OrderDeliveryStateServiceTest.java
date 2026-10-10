package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.enums.DeliveryTaskStatusEnum;
import com.aryn.cloud.order.api.enums.OrderItemStatusEnum;
import com.aryn.cloud.order.api.enums.OrderStatusEnum;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商城配送/内部配送的订单状态联动。
 *
 * <p>回归守门：历史上只有第三方快递的 deliverOrder 会把订单推进到「待收货」，
 * way=3/4 的订单永远停留在「待发货」，客户无法确认收货、订单无法完成。
 */
class OrderDeliveryStateServiceTest {

	private OrderInfoMapper orderInfoMapper;

	private OrderItemMapper orderItemMapper;

	private OrderDeliveryStateService service;

	private com.aryn.cloud.order.mapper.DeliveryTaskMapper deliveryTaskMapper;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), OrderInfo.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), OrderItemEntity.class);
		orderInfoMapper = mock(OrderInfoMapper.class);
		orderItemMapper = mock(OrderItemMapper.class);
		deliveryTaskMapper = mock(com.aryn.cloud.order.mapper.DeliveryTaskMapper.class);
		service = new OrderDeliveryStateService(orderInfoMapper, orderItemMapper,
				mock(OrderWxDeliveryService.class), deliveryTaskMapper);
	}

	private DeliveryTask task(String orderId) {
		DeliveryTask task = new DeliveryTask();
		task.setId("task-1");
		task.setOrderId(orderId);
		task.setTenantId("tenant-1");
		return task;
	}

	private OrderInfo order(String way, String status) {
		OrderInfo orderInfo = new OrderInfo();
		orderInfo.setId("order-1");
		orderInfo.setDeliveryWay(way);
		orderInfo.setStatus(status);
		return orderInfo;
	}

	@Test
	@DisplayName("商城配送订单在商品离仓后推进到待收货，并同步订单项与发货时间")
	void mallDeliveryAdvancesOrderToWaitingReceipt() {
		when(orderInfoMapper.selectById("order-1"))
				.thenReturn(order(MallOrderConstants.DELIVERY_WAY_3, OrderStatusEnum.WAITING_FOR_DELIVERY.getCode()));
		when(orderInfoMapper.update(isNull(), any())).thenReturn(1);
		when(orderItemMapper.update(isNull(), any())).thenReturn(2);

		assertThat(service.markShippedOnPickUp(task("order-1"))).isTrue();

		verify(orderInfoMapper).update(isNull(), any());
		verify(orderItemMapper).update(isNull(), any());
	}

	@Test
	@DisplayName("公司内部配送（way=4）同样推进到待收货")
	void internalDeliveryAdvancesOrderToWaitingReceipt() {
		when(orderInfoMapper.selectById("order-1"))
				.thenReturn(order(MallOrderConstants.DELIVERY_WAY_4, OrderStatusEnum.WAITING_FOR_DELIVERY.getCode()));
		when(orderInfoMapper.update(isNull(), any())).thenReturn(1);

		assertThat(service.markShippedOnPickUp(task("order-1"))).isTrue();

		verify(orderItemMapper).update(isNull(), any());
	}

	@Test
	@DisplayName("第三方快递订单不受影响，仍由发货单驱动")
	void expressOrderIsNotTouchedByDeliveryTask() {
		when(orderInfoMapper.selectById("order-1"))
				.thenReturn(order(MallOrderConstants.DELIVERY_WAY_1, OrderStatusEnum.WAITING_FOR_DELIVERY.getCode()));

		assertThat(service.markShippedOnPickUp(task("order-1"))).isFalse();

		verify(orderInfoMapper, never()).update(isNull(), any());
		verify(orderItemMapper, never()).update(isNull(), any());
	}

	@Test
	@DisplayName("重复出发按幂等处理：订单已是待收货时不再重复推进")
	void repeatedDepartIsIdempotent() {
		when(orderInfoMapper.selectById("order-1"))
				.thenReturn(order(MallOrderConstants.DELIVERY_WAY_3, OrderStatusEnum.WAITING_FOR_DELIVERY.getCode()));
		// 条件更新未命中：说明状态已被并发事务推进
		when(orderInfoMapper.update(isNull(), any())).thenReturn(0);
		when(orderInfoMapper.selectById("order-1"))
				.thenReturn(order(MallOrderConstants.DELIVERY_WAY_3, OrderStatusEnum.WAITING_FOR_RECEIPT.getCode()));

		assertThat(service.markShippedOnPickUp(task("order-1"))).isTrue();

		verify(orderItemMapper, never()).update(isNull(), any());
	}

	@Test
	@DisplayName("已取消或已完成的订单不再回退状态")
	void terminalOrdersAreNotRolledBack() {
		when(orderInfoMapper.selectById("order-1"))
				.thenReturn(order(MallOrderConstants.DELIVERY_WAY_3, OrderStatusEnum.CANCELED.getCode()));

		assertThat(service.markShippedOnPickUp(task("order-1"))).isFalse();

		verify(orderInfoMapper, never()).update(isNull(), any());
	}

	@Test
	@DisplayName("配送方式判定只覆盖商城配送与公司内部配送")
	void taskDrivenDeliveryWays() {
		assertThat(OrderDeliveryStateService.isTaskDrivenDeliveryWay(MallOrderConstants.DELIVERY_WAY_3)).isTrue();
		assertThat(OrderDeliveryStateService.isTaskDrivenDeliveryWay(MallOrderConstants.DELIVERY_WAY_4)).isTrue();
		assertThat(OrderDeliveryStateService.isTaskDrivenDeliveryWay(MallOrderConstants.DELIVERY_WAY_1)).isFalse();
		assertThat(OrderDeliveryStateService.isTaskDrivenDeliveryWay(MallOrderConstants.DELIVERY_WAY_2)).isFalse();
		assertThat(OrderItemStatusEnum.SHIPPED.getCode()).isEqualTo("2");
		assertThat(List.of(OrderStatusEnum.WAITING_FOR_RECEIPT.getCode())).containsExactly("3");
	}

	private OrderInfo orderWithTask(String way, String status, DeliveryTask task) {
		OrderInfo orderInfo = order(way, status);
		orderInfo.setDeliveryTask(task);
		return orderInfo;
	}

	private DeliveryTask taskWithStatus(String status) {
		DeliveryTask task = task("order-1");
		task.setStatus(status);
		return task;
	}

	@Test
	@DisplayName("已完成订单视为已送达")
	void completedOrderIsDelivered() {
		assertThat(service.isDelivered(order(MallOrderConstants.DELIVERY_WAY_1, OrderStatusEnum.COMPLETED.getCode())))
				.isTrue();
	}

	@Test
	@DisplayName("待发货/待付款/已取消一律未送达")
	void nonReceiptStatesAreNotDelivered() {
		assertThat(service.isDelivered(order(MallOrderConstants.DELIVERY_WAY_3,
				OrderStatusEnum.WAITING_FOR_DELIVERY.getCode()))).isFalse();
		assertThat(service.isDelivered(order(MallOrderConstants.DELIVERY_WAY_3,
				OrderStatusEnum.WAITING_FOR_PAYMENT.getCode()))).isFalse();
		assertThat(service.isDelivered(order(MallOrderConstants.DELIVERY_WAY_3,
				OrderStatusEnum.CANCELED.getCode()))).isFalse();
	}

	@Test
	@DisplayName("待收货时：配送任务已送达或已签收即为已送达")
	void deliveredWhenTaskArrivedOrSigned() {
		assertThat(service.isDelivered(orderWithTask(MallOrderConstants.DELIVERY_WAY_4,
				OrderStatusEnum.WAITING_FOR_RECEIPT.getCode(),
				taskWithStatus(DeliveryTaskStatusEnum.ARRIVED.getCode())))).isTrue();
		assertThat(service.isDelivered(orderWithTask(MallOrderConstants.DELIVERY_WAY_4,
				OrderStatusEnum.WAITING_FOR_RECEIPT.getCode(),
				taskWithStatus(DeliveryTaskStatusEnum.SIGNED.getCode())))).isTrue();
	}

	@Test
	@DisplayName("待收货但配送任务仍在途（待送达/配货中）不算已送达")
	void inTransitTaskIsNotDelivered() {
		assertThat(service.isDelivered(orderWithTask(MallOrderConstants.DELIVERY_WAY_4,
				OrderStatusEnum.WAITING_FOR_RECEIPT.getCode(),
				taskWithStatus(DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode())))).isFalse();
		assertThat(service.isDelivered(orderWithTask(MallOrderConstants.DELIVERY_WAY_4,
				OrderStatusEnum.WAITING_FOR_RECEIPT.getCode(),
				taskWithStatus(DeliveryTaskStatusEnum.PICKING.getCode())))).isFalse();
	}

	@Test
	@DisplayName("待收货且任务未回填时按订单ID实时查询配送任务")
	void loadsTaskWhenNotPrefilled() {
		OrderInfo orderInfo = order(MallOrderConstants.DELIVERY_WAY_4, OrderStatusEnum.WAITING_FOR_RECEIPT.getCode());
		when(deliveryTaskMapper.selectOne(any()))
				.thenReturn(taskWithStatus(DeliveryTaskStatusEnum.ARRIVED.getCode()));

		assertThat(service.isDelivered(orderInfo)).isTrue();
	}

	@Test
	@DisplayName("上门自提到店即已送达；第三方快递只能等买家确认收货")
	void pickupAndExpressWays() {
		assertThat(service.isDelivered(order(MallOrderConstants.DELIVERY_WAY_2,
				OrderStatusEnum.WAITING_FOR_RECEIPT.getCode()))).isTrue();
		// 快递无客观妥投信号，待收货阶段不算送达
		assertThat(service.isDelivered(order(MallOrderConstants.DELIVERY_WAY_1,
				OrderStatusEnum.WAITING_FOR_RECEIPT.getCode()))).isFalse();
	}

	// ---------------- 确认收货守卫 isReadyToReceive ----------------

	@Test
	@DisplayName("守卫：商城配送/内部配送必须等配送任务已送达或已签收")
	void readyToReceiveRequiresTaskDelivered() {
		assertThat(service.isReadyToReceive(orderWithTask(MallOrderConstants.DELIVERY_WAY_4,
				OrderStatusEnum.WAITING_FOR_RECEIPT.getCode(),
				taskWithStatus(DeliveryTaskStatusEnum.ARRIVED.getCode())))).isTrue();
		assertThat(service.isReadyToReceive(orderWithTask(MallOrderConstants.DELIVERY_WAY_3,
				OrderStatusEnum.WAITING_FOR_RECEIPT.getCode(),
				taskWithStatus(DeliveryTaskStatusEnum.SIGNED.getCode())))).isTrue();
	}

	@Test
	@DisplayName("守卫：司机尚未送达（待送达/配货中）时不得确认收货")
	void readyToReceiveRejectedWhileInTransit() {
		assertThat(service.isReadyToReceive(orderWithTask(MallOrderConstants.DELIVERY_WAY_4,
				OrderStatusEnum.WAITING_FOR_RECEIPT.getCode(),
				taskWithStatus(DeliveryTaskStatusEnum.WAITING_ARRIVE.getCode())))).isFalse();
		assertThat(service.isReadyToReceive(orderWithTask(MallOrderConstants.DELIVERY_WAY_4,
				OrderStatusEnum.WAITING_FOR_RECEIPT.getCode(),
				taskWithStatus(DeliveryTaskStatusEnum.PICKING.getCode())))).isFalse();
	}

	@Test
	@DisplayName("守卫 fail-closed：任务缺失时不得确认收货")
	void readyToReceiveRejectedWhenTaskMissing() {
		OrderInfo orderInfo = order(MallOrderConstants.DELIVERY_WAY_4, OrderStatusEnum.WAITING_FOR_RECEIPT.getCode());
		assertThat(service.isReadyToReceive(orderInfo)).isFalse();
	}

	@Test
	@DisplayName("守卫：未回填任务时按订单ID实时查询，查到已送达则放行")
	void readyToReceiveLoadsTaskWhenNotPrefilled() {
		OrderInfo orderInfo = order(MallOrderConstants.DELIVERY_WAY_4, OrderStatusEnum.WAITING_FOR_RECEIPT.getCode());
		when(deliveryTaskMapper.selectOne(any()))
				.thenReturn(taskWithStatus(DeliveryTaskStatusEnum.ARRIVED.getCode()));

		assertThat(service.isReadyToReceive(orderInfo)).isTrue();
	}

	@Test
	@DisplayName("守卫：快递与自提不受配送任务限制，可确认收货")
	void readyToReceiveAllowsExpressAndPickup() {
		assertThat(service.isReadyToReceive(order(MallOrderConstants.DELIVERY_WAY_1,
				OrderStatusEnum.WAITING_FOR_RECEIPT.getCode()))).isTrue();
		assertThat(service.isReadyToReceive(order(MallOrderConstants.DELIVERY_WAY_2,
				OrderStatusEnum.WAITING_FOR_RECEIPT.getCode()))).isTrue();
	}

	@Test
	@DisplayName("守卫：空订单拒绝，已完成订单幂等放行")
	void readyToReceiveNullOrCompleted() {
		assertThat(service.isReadyToReceive(null)).isFalse();
		assertThat(service.isReadyToReceive(order(MallOrderConstants.DELIVERY_WAY_4,
				OrderStatusEnum.COMPLETED.getCode()))).isTrue();
	}

}
