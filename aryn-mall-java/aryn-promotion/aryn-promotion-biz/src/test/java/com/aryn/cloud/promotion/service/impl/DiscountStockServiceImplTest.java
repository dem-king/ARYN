package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.dto.DiscountStockItemDTO;
import com.aryn.cloud.promotion.api.entity.DiscountActivity;
import com.aryn.cloud.promotion.api.entity.DiscountGoods;
import com.aryn.cloud.promotion.api.entity.DiscountStockLock;
import com.aryn.cloud.promotion.mapper.DiscountActivityMapper;
import com.aryn.cloud.promotion.mapper.DiscountGoodsMapper;
import com.aryn.cloud.promotion.mapper.DiscountStockLockMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
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
 * 折扣库存占用：商品级与全场活动级两条路径的核心口径。
 *
 * <p>四条硬约束：未配置限量不动库存、重复调用不重复扣减、扣减失败整单拦截、
 * 额度归属必须与「实际生效的活动」一致（全场扣活动、指定商品扣商品行）。
 */
class DiscountStockServiceImplTest {

	private DiscountActivityMapper activityMapper;
	private DiscountGoodsMapper goodsMapper;
	private DiscountStockLockMapper lockMapper;
	private RemoteGoodsSkuService remoteGoodsSkuService;
	private DiscountStockServiceImpl service;

	@BeforeEach
	void setUp() {
		activityMapper = mock(DiscountActivityMapper.class);
		goodsMapper = mock(DiscountGoodsMapper.class);
		lockMapper = mock(DiscountStockLockMapper.class);
		remoteGoodsSkuService = mock(RemoteGoodsSkuService.class);
		service = new DiscountStockServiceImpl(activityMapper, goodsMapper, lockMapper);
		ReflectionTestUtils.setField(service, "remoteGoodsSkuService", remoteGoodsSkuService);
		when(remoteGoodsSkuService.getBySkuIds(any())).thenReturn(List.of(
				new GoodsSku().setId("sku-1").setSalesPrice(BigDecimal.valueOf(100))));
		when(lockMapper.selectCount(any())).thenReturn(0L);
	}

	private DiscountActivity activity(int scope, Integer stock) {
		DiscountActivity activity = new DiscountActivity();
		activity.setId("act-1");
		activity.setScope(scope);
		activity.setStock(stock);
		activity.setSoldCount(0);
		activity.setStatus(1);
		activity.setDiscountType(1);
		activity.setDiscountValue(BigDecimal.valueOf(0.8));
		activity.setStartTime(LocalDateTime.now().minusHours(1));
		activity.setEndTime(LocalDateTime.now().plusHours(1));
		return activity;
	}

	private void stubFullScope(Integer stock) {
		when(activityMapper.selectList(any())).thenReturn(List.of(activity(1, stock)));
	}

	private void stubScopedGoods(Integer stock) {
		when(activityMapper.selectList(any())).thenReturn(List.of(activity(2, null)));
		DiscountGoods goods = new DiscountGoods().setId("goods-1").setActivityId("act-1")
			.setSkuId("sku-1").setStock(stock).setSoldCount(0);
		when(goodsMapper.selectOne(any())).thenReturn(goods);
	}

	@Test
	void fullScopeReserveDeductsActivityStock() {
		stubFullScope(50);
		when(activityMapper.tryReserveStock("act-1", 2)).thenReturn(1);

		var result = service.reserve("tenant-1", "order-1", List.of(new DiscountStockItemDTO("sku-1", 2)));

		assertThat(result.isSuccess()).isTrue();
		verify(activityMapper).tryReserveStock("act-1", 2);
		// 全场型不碰商品行额度
		verify(goodsMapper, never()).tryReserveStock(anyString(), anyInt());
	}

	@Test
	void scopedReserveDeductsGoodsStock() {
		stubScopedGoods(10);
		when(goodsMapper.tryReserveStock("goods-1", 2)).thenReturn(1);

		service.reserve("tenant-1", "order-1", List.of(new DiscountStockItemDTO("sku-1", 2)));

		verify(goodsMapper).tryReserveStock("goods-1", 2);
		verify(activityMapper, never()).tryReserveStock(anyString(), anyInt());
	}

	@Test
	void fullScopeWithoutStockLimitIsSkipped() {
		stubFullScope(null);

		service.reserve("tenant-1", "order-1", List.of(new DiscountStockItemDTO("sku-1", 2)));

		verify(lockMapper, never()).insert(any(DiscountStockLock.class));
		verify(activityMapper, never()).tryReserveStock(anyString(), anyInt());
	}

	@Test
	void reserveFailsWhenActivityStockIsGone() {
		stubFullScope(1);
		when(activityMapper.tryReserveStock("act-1", 2)).thenReturn(0);

		assertThatThrownBy(() -> service.reserve("tenant-1", "order-1",
				List.of(new DiscountStockItemDTO("sku-1", 2))))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(ex -> assertThat(((ArynBusinessException) ex).getMsg()).contains("库存不足"));
	}

	@Test
	void reserveIsIdempotentWhenLockAlreadyExists() {
		stubFullScope(10);
		when(lockMapper.selectCount(any())).thenReturn(1L);

		service.reserve("tenant-1", "order-1", List.of(new DiscountStockItemDTO("sku-1", 2)));

		verify(lockMapper, never()).insert(any(DiscountStockLock.class));
		verify(activityMapper, never()).tryReserveStock(anyString(), anyInt());
	}

	@Test
	void reserveIgnoresInvalidItems() {
		service.reserve("tenant-1", "order-1", List.of(new DiscountStockItemDTO(" ", 0)));

		verifyNoInteractions(activityMapper, goodsMapper, lockMapper);
	}

	@Test
	void releaseRestoresGoodsStockForScopedActivity() {
		DiscountStockLock lock = new DiscountStockLock().setActivityId("act-1").setSkuId("sku-1")
			.setQuantity(2).setStatus(DiscountStockLock.STATUS_LOCKED);
		when(lockMapper.selectList(any())).thenReturn(List.of(lock));
		DiscountActivity activity = activity(2, null);
		when(activityMapper.selectById("act-1")).thenReturn(activity);
		when(goodsMapper.selectOne(any())).thenReturn(
				new DiscountGoods().setId("goods-1").setStock(10).setSoldCount(2));
		when(lockMapper.releaseByOrder("tenant-1", "order-1", "cancel")).thenReturn(1);

		assertThat(service.release("tenant-1", "order-1", null, "cancel")).isEqualTo(1);
		verify(goodsMapper).restoreStock("goods-1", 2);
		verify(activityMapper, never()).restoreStock(anyString(), anyInt());
	}

	@Test
	void releaseRestoresActivityStockForFullScope() {
		DiscountStockLock lock = new DiscountStockLock().setActivityId("act-1").setSkuId("sku-1")
			.setQuantity(2).setStatus(DiscountStockLock.STATUS_LOCKED);
		when(lockMapper.selectList(any())).thenReturn(List.of(lock));
		when(activityMapper.selectById("act-1")).thenReturn(activity(1, 50));
		when(lockMapper.releaseByOrder("tenant-1", "order-1", "cancel")).thenReturn(1);

		assertThat(service.release("tenant-1", "order-1", null, "cancel")).isEqualTo(1);
		verify(activityMapper).restoreStock("act-1", 2);
		verify(goodsMapper, never()).restoreStock(anyString(), anyInt());
	}

	@Test
	void partialRefundReleasesOnlyThatOrderItem() {
		DiscountStockLock item1 = new DiscountStockLock().setActivityId("act-1").setSkuId("sku-1")
			.setOrderItemId("item-1").setQuantity(1).setStatus(DiscountStockLock.STATUS_LOCKED);
		when(lockMapper.selectList(any())).thenReturn(List.of(item1));
		when(activityMapper.selectById("act-1")).thenReturn(activity(1, 50));
		when(lockMapper.releaseByOrderItem("tenant-1", "order-1", "item-1", "REFUND")).thenReturn(1);

		assertThat(service.release("tenant-1", "order-1", "item-1", "REFUND")).isEqualTo(1);
		verify(lockMapper).releaseByOrderItem("tenant-1", "order-1", "item-1", "REFUND");
		verify(lockMapper, never()).releaseByOrder(anyString(), anyString(), anyString());
	}

	@Test
	void releaseWithoutLocksIsNoop() {
		when(lockMapper.selectList(any())).thenReturn(List.of());

		assertThat(service.release("tenant-1", "order-1", null, "cancel")).isZero();
		verify(activityMapper, never()).restoreStock(anyString(), anyInt());
		verify(goodsMapper, never()).restoreStock(anyString(), anyInt());
	}

}
