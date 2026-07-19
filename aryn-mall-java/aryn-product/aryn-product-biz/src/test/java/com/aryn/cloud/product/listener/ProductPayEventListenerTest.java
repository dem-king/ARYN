package com.aryn.cloud.product.listener;

import com.aryn.cloud.common.core.entity.OrderItemPaySuccessEvent;
import com.aryn.cloud.common.core.entity.OrderPaySuccessEvent;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.mapper.ProductOrderPayRecordMapper;
import com.aryn.cloud.product.service.IGoodsSpuService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductPayEventListenerTest {

	@AfterEach
	void clearTenant() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void clearsTenantContextWhenSalesUpdateFails() {
		IGoodsSpuService goodsSpuService = mock(IGoodsSpuService.class);
		ProductOrderPayRecordMapper recordMapper = mock(ProductOrderPayRecordMapper.class);
		when(recordMapper.insertIfAbsent(org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.eq("order-1"), org.mockito.ArgumentMatchers.eq("tenant-1"))).thenReturn(1);
		doThrow(new IllegalStateException("update failed"))
			.when(goodsSpuService).updateSalesVolume("spu-1", 2);
		ProductPayEventListener listener = new ProductPayEventListener(goodsSpuService, recordMapper);
		OrderItemPaySuccessEvent item = new OrderItemPaySuccessEvent();
		item.setSpuId("spu-1");
		item.setBuyQuantity(2);
		OrderPaySuccessEvent event = new OrderPaySuccessEvent();
		event.setOrderId("order-1");
		event.setTenantId("tenant-1");
		event.setItemList(List.of(item));

		assertThatThrownBy(() -> listener.onMessage(event))
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("update failed");
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void duplicateOrderDoesNotIncreaseSalesAgain() {
		IGoodsSpuService goodsSpuService = mock(IGoodsSpuService.class);
		ProductOrderPayRecordMapper recordMapper = mock(ProductOrderPayRecordMapper.class);
		when(recordMapper.insertIfAbsent(org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.eq("order-1"), org.mockito.ArgumentMatchers.eq("tenant-1"))).thenReturn(0);
		ProductPayEventListener listener = new ProductPayEventListener(goodsSpuService, recordMapper);

		listener.onMessage(event("order-1", List.of(item("spu-1", 2))));

		verify(goodsSpuService, never()).updateSalesVolume(org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.anyInt());
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void aggregatesSameSpuBeforeIncreasingSales() {
		IGoodsSpuService goodsSpuService = mock(IGoodsSpuService.class);
		ProductOrderPayRecordMapper recordMapper = mock(ProductOrderPayRecordMapper.class);
		when(recordMapper.insertIfAbsent(org.mockito.ArgumentMatchers.anyString(),
			org.mockito.ArgumentMatchers.eq("order-1"), org.mockito.ArgumentMatchers.eq("tenant-1"))).thenReturn(1);
		when(goodsSpuService.updateSalesVolume("spu-1", 3)).thenReturn(true);
		ProductPayEventListener listener = new ProductPayEventListener(goodsSpuService, recordMapper);

		listener.onMessage(event("order-1", List.of(item("spu-1", 1), item("spu-1", 2))));

		verify(goodsSpuService).updateSalesVolume("spu-1", 3);
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	private OrderPaySuccessEvent event(String orderId, List<OrderItemPaySuccessEvent> items) {
		OrderPaySuccessEvent event = new OrderPaySuccessEvent();
		event.setOrderId(orderId);
		event.setTenantId("tenant-1");
		event.setItemList(items);
		return event;
	}

	private OrderItemPaySuccessEvent item(String spuId, int quantity) {
		OrderItemPaySuccessEvent item = new OrderItemPaySuccessEvent();
		item.setSpuId(spuId);
		item.setBuyQuantity(quantity);
		return item;
	}
}
