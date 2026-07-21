package com.aryn.cloud.product.listener;

import com.aryn.cloud.common.core.entity.OrderItemRefundSuccessEvent;
import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.mapper.ProductRefundStockRecordMapper;
import com.aryn.cloud.product.service.IGoodsSkuService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductRefundListenerTest {

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void duplicateRefundMessageRestoresStockOnlyOnce() {
		IGoodsSkuService goodsSkuService = mock(IGoodsSkuService.class);
		ProductRefundStockRecordMapper recordMapper = mock(ProductRefundStockRecordMapper.class);
		when(recordMapper.insertIfAbsent(anyString(), anyString(), anyString())).thenReturn(1, 0);
		ProductRefundListener listener = new ProductRefundListener(goodsSkuService, recordMapper);
		OrderRefundSuccessEvent event = refund("refund-1");

		listener.onMessage(event);
		listener.onMessage(event);

		verify(goodsSkuService).rollbackStockList(anyList());
	}

	@Test
	void rejectsMissingRefundNumberBeforeStockWrite() {
		IGoodsSkuService goodsSkuService = mock(IGoodsSkuService.class);
		ProductRefundListener listener = new ProductRefundListener(goodsSkuService,
				mock(ProductRefundStockRecordMapper.class));
		OrderRefundSuccessEvent event = refund(null);

		assertThatThrownBy(() -> listener.onMessage(event)).isInstanceOf(IllegalArgumentException.class);
		verify(goodsSkuService, never()).rollbackStockList(anyList());
	}

	@Test
	void clearsTenantContextAfterProcessing() {
		IGoodsSkuService goodsSkuService = mock(IGoodsSkuService.class);
		ProductRefundStockRecordMapper recordMapper = mock(ProductRefundStockRecordMapper.class);
		when(recordMapper.insertIfAbsent(anyString(), anyString(), anyString())).thenReturn(1);
		ProductRefundListener listener = new ProductRefundListener(goodsSkuService, recordMapper);
		ArynTenantContextHolder.setTenantId("stale-tenant");

		listener.onMessage(refund("refund-1"));

		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	private OrderRefundSuccessEvent refund(String refundNo) {
		OrderItemRefundSuccessEvent item = new OrderItemRefundSuccessEvent();
		item.setSkuId("sku-1");
		item.setSpuId("spu-1");
		item.setBuyQuantity(2);
		OrderRefundSuccessEvent event = new OrderRefundSuccessEvent();
		event.setTenantId("tenant-1");
		event.setRefundNo(refundNo);
		event.setOrderItem(item);
		return event;
	}

}
