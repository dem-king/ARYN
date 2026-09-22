package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
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

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), OrderInfo.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), OrderItemEntity.class);
		orderInfoMapper = mock(OrderInfoMapper.class);
		orderItemMapper = mock(OrderItemMapper.class);
		service = new OrderDeliveryStateService(orderInfoMapper, orderItemMapper,
				mock(OrderWxDeliveryService.class));
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

}
