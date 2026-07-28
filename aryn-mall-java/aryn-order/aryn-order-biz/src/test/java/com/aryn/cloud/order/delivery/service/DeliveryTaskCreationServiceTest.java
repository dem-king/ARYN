package com.aryn.cloud.order.delivery.service;

import com.aryn.cloud.order.api.constant.MallOrderConstants;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTask;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTaskItem;
import com.aryn.cloud.order.api.delivery.enums.DeliveryTaskStatusEnum;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskItemMapper;
import com.aryn.cloud.order.delivery.mapper.OrderDeliveryTaskMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryTaskCreationServiceTest {

	@Test
	void createsWaitingTaskAndOnePickingItemPerOrderItem() {
		OrderDeliveryTaskMapper taskMapper = mock(OrderDeliveryTaskMapper.class);
		OrderDeliveryTaskItemMapper itemMapper = mock(OrderDeliveryTaskItemMapper.class);
		when(taskMapper.insert(any(OrderDeliveryTask.class))).thenReturn(1);
		when(itemMapper.insert(any(OrderDeliveryTaskItem.class))).thenReturn(1);
		DeliveryTaskCreationService service = new DeliveryTaskCreationService(taskMapper, itemMapper);

		OrderDeliveryTask created = service.createIfNeeded(mallDeliveryOrder(), List.of(item("item-1"), item("item-2")));

		assertThat(created.getStatus()).isEqualTo(DeliveryTaskStatusEnum.WAITING_ASSIGNMENT.name());
		assertThat(created.getOrderId()).isEqualTo("order-1");
		assertThat(created.getOrderNo()).isEqualTo("ORDER-1");
		assertThat(created.getTenantId()).isEqualTo("tenant-1");
		assertThat(created.getAttemptNo()).isEqualTo(1);
		assertThat(created.getVersion()).isZero();
		ArgumentCaptor<OrderDeliveryTaskItem> itemCaptor = ArgumentCaptor.forClass(OrderDeliveryTaskItem.class);
		verify(itemMapper, org.mockito.Mockito.times(2)).insert(itemCaptor.capture());
		assertThat(itemCaptor.getAllValues()).extracting(OrderDeliveryTaskItem::getOrderItemId)
			.containsExactly("item-1", "item-2");
		assertThat(itemCaptor.getAllValues()).allSatisfy(taskItem -> {
			assertThat(taskItem.getTaskId()).isEqualTo(created.getId());
			assertThat(taskItem.getAttemptNo()).isEqualTo(1);
			assertThat(taskItem.getChecked()).isEqualTo("0");
			assertThat(taskItem.getTenantId()).isEqualTo("tenant-1");
		});
	}

	@Test
	void ignoresExpressAndPickupOrders() {
		OrderDeliveryTaskMapper taskMapper = mock(OrderDeliveryTaskMapper.class);
		OrderDeliveryTaskItemMapper itemMapper = mock(OrderDeliveryTaskItemMapper.class);
		DeliveryTaskCreationService service = new DeliveryTaskCreationService(taskMapper, itemMapper);
		OrderInfo order = mallDeliveryOrder();

		order.setDeliveryWay(MallOrderConstants.DELIVERY_WAY_1);
		assertThat(service.createIfNeeded(order, List.of(item("item-1")))).isNull();
		order.setDeliveryWay(MallOrderConstants.DELIVERY_WAY_2);
		assertThat(service.createIfNeeded(order, List.of(item("item-1")))).isNull();

		verify(taskMapper, never()).insert(any(OrderDeliveryTask.class));
		verify(itemMapper, never()).insert(any(OrderDeliveryTaskItem.class));
	}

	@Test
	void duplicateOrderReturnsExistingTaskWithoutDuplicatingItems() {
		OrderDeliveryTaskMapper taskMapper = mock(OrderDeliveryTaskMapper.class);
		OrderDeliveryTaskItemMapper itemMapper = mock(OrderDeliveryTaskItemMapper.class);
		OrderDeliveryTask existing = new OrderDeliveryTask().setId("task-existing");
		when(taskMapper.insert(any(OrderDeliveryTask.class))).thenThrow(new DuplicateKeyException("duplicate order"));
		when(taskMapper.selectByTenantAndOrderId("tenant-1", "order-1")).thenReturn(existing);
		DeliveryTaskCreationService service = new DeliveryTaskCreationService(taskMapper, itemMapper);

		OrderDeliveryTask result = service.createIfNeeded(mallDeliveryOrder(), List.of(item("item-1")));

		assertThat(result).isSameAs(existing);
		verify(itemMapper, never()).insert(any(OrderDeliveryTaskItem.class));
	}

	@Test
	void itemInsertFailurePropagatesToPaymentTransaction() {
		OrderDeliveryTaskMapper taskMapper = mock(OrderDeliveryTaskMapper.class);
		OrderDeliveryTaskItemMapper itemMapper = mock(OrderDeliveryTaskItemMapper.class);
		when(taskMapper.insert(any(OrderDeliveryTask.class))).thenReturn(1);
		when(itemMapper.insert(any(OrderDeliveryTaskItem.class)))
			.thenThrow(new IllegalStateException("item insert failed"));
		DeliveryTaskCreationService service = new DeliveryTaskCreationService(taskMapper, itemMapper);

		assertThatThrownBy(() -> service.createIfNeeded(mallDeliveryOrder(), List.of(item("item-1"))))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("item insert failed");
	}

	@Test
	void taskInsertFailurePropagatesToPaymentTransaction() {
		OrderDeliveryTaskMapper taskMapper = mock(OrderDeliveryTaskMapper.class);
		OrderDeliveryTaskItemMapper itemMapper = mock(OrderDeliveryTaskItemMapper.class);
		when(taskMapper.insert(any(OrderDeliveryTask.class))).thenReturn(0);
		DeliveryTaskCreationService service = new DeliveryTaskCreationService(taskMapper, itemMapper);

		assertThatThrownBy(() -> service.createIfNeeded(mallDeliveryOrder(), List.of(item("item-1"))))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg())
				.isEqualTo("商城配送任务创建失败，请重试"));
		verify(itemMapper, never()).insert(any(OrderDeliveryTaskItem.class));
	}

	private OrderInfo mallDeliveryOrder() {
		return new OrderInfo().setId("order-1")
			.setOrderNo("ORDER-1")
			.setTenantId("tenant-1")
			.setDeliveryWay(MallOrderConstants.DELIVERY_WAY_3);
	}

	private OrderItemEntity item(String id) {
		return new OrderItemEntity().setId(id).setOrderId("order-1").setTenantId("tenant-1");
	}

}
