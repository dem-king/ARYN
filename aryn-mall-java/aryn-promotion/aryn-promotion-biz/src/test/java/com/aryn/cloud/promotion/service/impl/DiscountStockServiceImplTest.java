package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.DiscountStockItemDTO;
import com.aryn.cloud.promotion.api.entity.DiscountActivity;
import com.aryn.cloud.promotion.api.entity.DiscountGoods;
import com.aryn.cloud.promotion.api.entity.DiscountStockLock;
import com.aryn.cloud.promotion.mapper.DiscountActivityMapper;
import com.aryn.cloud.promotion.mapper.DiscountGoodsMapper;
import com.aryn.cloud.promotion.mapper.DiscountStockLockMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 折扣商品级限量：占用/释放的核心口径。
 *
 * <p>锁的三条硬约束：未配置限量不动库存、重复调用不重复扣减、扣减失败整单拦截。
 */
class DiscountStockServiceImplTest {

	private DiscountGoodsMapper goodsMapper;
	private DiscountStockLockMapper lockMapper;
	private DiscountActivityMapper activityMapper;
	private DiscountStockServiceImpl service;

	@BeforeEach
	void setUp() {
		goodsMapper = mock(DiscountGoodsMapper.class);
		lockMapper = mock(DiscountStockLockMapper.class);
		activityMapper = mock(DiscountActivityMapper.class);
		service = new DiscountStockServiceImpl(goodsMapper, lockMapper, activityMapper);
	}

	private void stubActiveGoods(Integer stock) {
		DiscountGoods goods = new DiscountGoods()
			.setId("goods-1")
			.setActivityId("act-1")
			.setSkuId("sku-1")
			.setStock(stock)
			.setSoldCount(0);
		when(goodsMapper.selectList(any())).thenReturn(List.of(goods));
		DiscountActivity activity = new DiscountActivity();
		activity.setId("act-1");
		activity.setStatus(1);
		activity.setStartTime(LocalDateTime.now().minusHours(1));
		activity.setEndTime(LocalDateTime.now().plusHours(1));
		when(activityMapper.selectList(any())).thenReturn(List.of(activity));
	}

	@Test
	void reserveSkipsGoodsWithoutStockLimit() {
		stubActiveGoods(null);

		var result = service.reserve("tenant-1", "order-1", List.of(new DiscountStockItemDTO("sku-1", 2)));

		assertThat(result.isSuccess()).isTrue();
		verify(lockMapper, never()).insert(any(DiscountStockLock.class));
		verify(goodsMapper, never()).tryReserveStock(anyString(), anyInt());
	}

	@Test
	void reserveDeductsWhenStockConfigured() {
		stubActiveGoods(10);
		when(lockMapper.selectCount(any())).thenReturn(0L);
		when(goodsMapper.tryReserveStock("goods-1", 2)).thenReturn(1);

		var result = service.reserve("tenant-1", "order-1", List.of(new DiscountStockItemDTO("sku-1", 2)));

		assertThat(result.isSuccess()).isTrue();
		verify(lockMapper).insert(any(DiscountStockLock.class));
		verify(goodsMapper).tryReserveStock("goods-1", 2);
	}

	@Test
	void reserveIsIdempotentWhenLockAlreadyExists() {
		stubActiveGoods(10);
		when(lockMapper.selectCount(any())).thenReturn(1L);

		service.reserve("tenant-1", "order-1", List.of(new DiscountStockItemDTO("sku-1", 2)));

		verify(lockMapper, never()).insert(any(DiscountStockLock.class));
		verify(goodsMapper, never()).tryReserveStock(anyString(), anyInt());
	}

	@Test
	void reserveFailsWhenStockIsNotEnough() {
		stubActiveGoods(1);
		when(lockMapper.selectCount(any())).thenReturn(0L);
		when(goodsMapper.tryReserveStock("goods-1", 2)).thenReturn(0);

		assertThatThrownBy(() -> service.reserve("tenant-1", "order-1",
				List.of(new DiscountStockItemDTO("sku-1", 2))))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(ex -> assertThat(((ArynBusinessException) ex).getMsg()).contains("库存不足"));
	}

	@Test
	void reserveIgnoresInvalidItems() {
		service.reserve("tenant-1", "order-1", List.of(new DiscountStockItemDTO(" ", 0)));

		verifyNoInteractions(goodsMapper, lockMapper, activityMapper);
	}

	@Test
	void releaseRestoresStockAndMarksLockReleased() {
		DiscountStockLock lock = new DiscountStockLock()
			.setActivityId("act-1")
			.setSkuId("sku-1")
			.setQuantity(2)
			.setStatus(DiscountStockLock.STATUS_LOCKED);
		when(lockMapper.selectList(any())).thenReturn(List.of(lock));
		DiscountGoods goods = new DiscountGoods().setId("goods-1").setStock(10).setSoldCount(2);
		when(goodsMapper.selectOne(any())).thenReturn(goods);
		when(lockMapper.releaseByOrder("tenant-1", "order-1", "cancel")).thenReturn(1);

		int released = service.release("tenant-1", "order-1", "cancel");

		assertThat(released).isEqualTo(1);
		verify(goodsMapper).restoreStock("goods-1", 2);
		verify(lockMapper).releaseByOrder("tenant-1", "order-1", "cancel");
	}

	@Test
	void releaseWithoutLocksIsNoop() {
		when(lockMapper.selectList(any())).thenReturn(List.of());

		assertThat(service.release("tenant-1", "order-1", "cancel")).isZero();
		verify(goodsMapper, never()).restoreStock(anyString(), anyInt());
	}

}