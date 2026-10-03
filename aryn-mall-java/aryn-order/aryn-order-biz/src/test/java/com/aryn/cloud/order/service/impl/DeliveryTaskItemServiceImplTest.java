package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.order.api.entity.DeliveryTaskItem;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.service.IDeliveryTaskService;
import com.aryn.cloud.order.service.IDeliveryTripService;
import com.aryn.cloud.order.service.IOrderItemService;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeliveryTaskItemServiceImplTest {

	private DeliveryTaskItemServiceImpl service;

	private IOrderItemService orderItemService;

	private RemoteGoodsSpuService remoteGoodsSpuService;

	@BeforeEach
	void setUp() {
		orderItemService = mock(IOrderItemService.class);
		remoteGoodsSpuService = mock(RemoteGoodsSpuService.class);
		service = new DeliveryTaskItemServiceImpl(mock(IDeliveryTaskService.class), mock(IDeliveryTripService.class),
				orderItemService);
		ReflectionTestUtils.setField(service, "remoteGoodsSpuService", remoteGoodsSpuService);
	}

	@Test
	void fillCategoryNameResolvesCategoryThroughOrderItemAndSpu() {
		DeliveryTaskItem item = new DeliveryTaskItem();
		item.setOrderItemId("oi-1");

		OrderItemEntity orderItem = new OrderItemEntity();
		orderItem.setId("oi-1");
		orderItem.setSpuId("spu-1");
		when(orderItemService.listByIds(List.of("oi-1"))).thenReturn(List.of(orderItem));

		GoodsSpu spu = new GoodsSpu();
		spu.setId("spu-1");
		spu.setCategoryName("生鲜/冷藏水产");
		when(remoteGoodsSpuService.getSpuByIds(List.of("spu-1"))).thenReturn(List.of(spu));

		service.fillCategoryName(List.of(item));

		assertThat(item.getCategoryName()).isEqualTo("生鲜/冷藏水产");
	}

	@Test
	void fillCategoryNameKeepsNullWhenOrderItemMissingOrCategoryBlank() {
		DeliveryTaskItem linked = new DeliveryTaskItem();
		linked.setOrderItemId("oi-1");
		DeliveryTaskItem unlinked = new DeliveryTaskItem();

		OrderItemEntity orderItem = new OrderItemEntity();
		orderItem.setId("oi-1");
		orderItem.setSpuId("spu-1");
		when(orderItemService.listByIds(List.of("oi-1"))).thenReturn(List.of(orderItem));

		GoodsSpu noCategory = new GoodsSpu();
		noCategory.setId("spu-1");
		when(remoteGoodsSpuService.getSpuByIds(List.of("spu-1"))).thenReturn(List.of(noCategory));

		service.fillCategoryName(List.of(linked, unlinked));

		assertThat(linked.getCategoryName()).isNull();
		assertThat(unlinked.getCategoryName()).isNull();
	}

	@Test
	void fillCategoryNameSkipsRemoteCallWithoutLinkableItems() {
		DeliveryTaskItem item = new DeliveryTaskItem();

		service.fillCategoryName(List.of(item));

		verify(orderItemService, never()).listByIds(anyList());
		verify(remoteGoodsSpuService, never()).getSpuByIds(anyList());
	}

}
