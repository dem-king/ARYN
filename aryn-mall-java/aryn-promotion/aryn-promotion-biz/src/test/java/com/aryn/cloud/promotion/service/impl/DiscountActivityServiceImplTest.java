package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.dto.DiscountActivityDTO;
import com.aryn.cloud.promotion.api.dto.DiscountGoodsDTO;
import com.aryn.cloud.promotion.api.entity.DiscountActivity;
import com.aryn.cloud.promotion.api.entity.DiscountGoods;
import com.aryn.cloud.promotion.mapper.DiscountActivityMapper;
import com.aryn.cloud.promotion.service.IDiscountGoodsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 折扣活动编辑：已产生销量的商品不能被移除（P3-03）。
 *
 * <p>背景：updateWithGoods 是「删光商品行再重建」。若某 SKU 已被下单占用又被移除，
 * 占用明细会指向不存在的商品行，取消/退款时回补不到 —— 库存就此永久少一份。
 */
class DiscountActivityServiceImplTest {

	private IDiscountGoodsService discountGoodsService;
	private DiscountActivityMapper activityMapper;
	private DiscountActivityServiceImpl service;

	@BeforeEach
	void setUp() {
		discountGoodsService = mock(IDiscountGoodsService.class);
		activityMapper = mock(DiscountActivityMapper.class);
		service = new DiscountActivityServiceImpl(discountGoodsService, mock(RemoteGoodsSkuService.class));
		ReflectionTestUtils.setField(service, "baseMapper", activityMapper);
	}

	private void stubActivity(boolean soldGoodsExists) {
		DiscountActivity activity = new DiscountActivity();
		activity.setId("act-1");
		activity.setScope(2);
		when(activityMapper.selectById("act-1")).thenReturn(activity);
		when(activityMapper.updateById(any(DiscountActivity.class))).thenReturn(1);
		when(discountGoodsService.listByActivityId("act-1")).thenReturn(List.of(
				new DiscountGoods().setActivityId("act-1").setSkuId("sku-kept").setStock(10).setSoldCount(0),
				new DiscountGoods().setActivityId("act-1").setSkuId("sku-sold").setStock(10)
					.setSoldCount(soldGoodsExists ? 3 : 0)));
	}

	private DiscountActivityDTO dtoWithSkus(String... skuIds) {
		DiscountActivityDTO dto = new DiscountActivityDTO();
		dto.setId("act-1");
		dto.setScope(2);
		dto.setDiscountType(1);
		dto.setDiscountValue(java.math.BigDecimal.valueOf(0.8));
		dto.setGoodsList(java.util.Arrays.stream(skuIds)
			.map(sku -> {
				DiscountGoodsDTO goods = new DiscountGoodsDTO();
				goods.setSkuId(sku);
				goods.setSpuId("spu-1");
				goods.setStock(10);
				return goods;
			})
			.toList());
		return dto;
	}

	@Test
	void removingSoldSkuIsRejected() {
		stubActivity(true);

		assertThatThrownBy(() -> service.updateWithGoods(dtoWithSkus("sku-kept")))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(ex -> assertThat(((ArynBusinessException) ex).getMsg()).contains("不能移除"));

		// 拒绝发生在删除之前：商品行与占用都不该被动过
		verify(discountGoodsService, never()).remove(any());
		verify(discountGoodsService, never()).saveBatch(anyList());
	}

	@Test
	void removingUnsoldSkuIsAllowed() {
		stubActivity(false);
		when(discountGoodsService.saveBatch(anyList())).thenReturn(true);

		assertThat(service.updateWithGoods(dtoWithSkus("sku-kept"))).isTrue();
		verify(discountGoodsService).remove(any());
		verify(discountGoodsService).saveBatch(anyList());
	}

	@Test
	void loweringStockBelowSoldIsRejected() {
		stubActivity(true);
		DiscountActivityDTO dto = dtoWithSkus("sku-kept", "sku-sold");
		dto.getGoodsList().stream()
			.filter(goods -> "sku-sold".equals(goods.getSkuId()))
			.findFirst()
			.orElseThrow()
			.setStock(1);

		assertThatThrownBy(() -> service.updateWithGoods(dto))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(ex -> assertThat(((ArynBusinessException) ex).getMsg()).contains("不能小于已售"));
	}

}
