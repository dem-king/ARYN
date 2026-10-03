package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.QuickCartInfoVO;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 快捷加购服务测试。
 *
 * <p>船供包装资料（MOQ/步长/采购单位）已下线（2026-09-29），
 * 快捷加购只负责挑出可售 SKU 与价格库存，数量规则由前端按 1 兜底。
 */
class QuickCartServiceImplTest {

	private GoodsSpuMapper goodsSpuMapper;

	private QuickCartServiceImpl service;

	@BeforeEach
	void setUp() {
		goodsSpuMapper = mock(GoodsSpuMapper.class);
		service = new QuickCartServiceImpl(goodsSpuMapper);
	}

	@Test
	void returnsNullForMissingSpuId() {
		assertThat(service.getQuickCartInfo(null)).isNull();
		assertThat(service.getQuickCartInfo("  ")).isNull();
	}

	@Test
	void returnsNullWhenGoodsIsOffShelf() {
		when(goodsSpuMapper.selectApiSpuById("spu-1")).thenReturn(null);

		assertThat(service.getQuickCartInfo("spu-1")).isNull();
	}

	@Test
	void singleSpecReturnsDirectMode() {
		GoodsSpu spu = spu("spu-1", "0", List.of(sku("sku-1", 10, "0")));
		spu.setSpuUrls(new String[]{"https://cdn/spu.jpg"});
		when(goodsSpuMapper.selectApiSpuById("spu-1")).thenReturn(spu);

		QuickCartInfoVO info = service.getQuickCartInfo("spu-1");

		assertThat(info.getMode()).isEqualTo(QuickCartInfoVO.MODE_DIRECT);
		assertThat(info.getSkuId()).isEqualTo("sku-1");
		assertThat(info.getSalesPrice()).isEqualByComparingTo("10");
		assertThat(info.getStock()).isEqualTo(5);
		// SKU 无图时回落到商品主图，避免加购弹层出现空图
		assertThat(info.getPicUrl()).isEqualTo("https://cdn/spu.jpg");
	}

	@Test
	void multiSpecReturnsChooseWithSellableSkus() {
		GoodsSpu spu = spu("spu-1", "1", List.of(sku("sku-1", 10, "0"), sku("sku-2", 20, "0")));
		when(goodsSpuMapper.selectApiSpuById("spu-1")).thenReturn(spu);

		QuickCartInfoVO info = service.getQuickCartInfo("spu-1");

		assertThat(info.getMode()).isEqualTo(QuickCartInfoVO.MODE_CHOOSE);
		assertThat(info.getGoodsSkus()).hasSize(2);
	}

	@Test
	void disabledAndOutOfStockSkusAreExcluded() {
		GoodsSku disabled = sku("sku-off", 10, "1");
		GoodsSku soldOut = sku("sku-empty", 10, "0");
		soldOut.setStock(0);
		GoodsSpu spu = spu("spu-1", "1", List.of(disabled, soldOut));
		when(goodsSpuMapper.selectApiSpuById("spu-1")).thenReturn(spu);

		QuickCartInfoVO info = service.getQuickCartInfo("spu-1");

		// 无可售 SKU 时不能伪装成「可选规格」，否则用户点进去才发现买不了
		assertThat(info.getMode()).isEqualTo(QuickCartInfoVO.MODE_UNAVAILABLE);
		assertThat(info.getReason()).contains("缺货");
	}

	@Test
	void singleSpecPrefersMostStockedSku() {
		GoodsSpu spu = spu("spu-1", "0", List.of(sku("sku-small", 10, "0"), sku("sku-big", 10, "0")));
		spu.getGoodsSkus().get(0).setStock(2);
		spu.getGoodsSkus().get(1).setStock(9);
		when(goodsSpuMapper.selectApiSpuById("spu-1")).thenReturn(spu);

		QuickCartInfoVO info = service.getQuickCartInfo("spu-1");

		assertThat(info.getSkuId()).isEqualTo("sku-big");
	}

	private GoodsSpu spu(String id, String enableSpecs, List<GoodsSku> skus) {
		GoodsSpu spu = new GoodsSpu();
		spu.setId(id);
		spu.setName("测试商品");
		spu.setEnableSpecs(enableSpecs);
		spu.setGoodsSkus(skus);
		return spu;
	}

	private GoodsSku sku(String id, int salesPrice, String status) {
		GoodsSku sku = new GoodsSku();
		sku.setId(id);
		sku.setSpuId("spu-1");
		sku.setSalesPrice(BigDecimal.valueOf(salesPrice));
		sku.setStock(5);
		sku.setStatus(status);
		sku.setSpecsArr(List.of(new GoodsSku.Specs("spec-1", "规格", "value-1", "默认")));
		return sku;
	}

}
