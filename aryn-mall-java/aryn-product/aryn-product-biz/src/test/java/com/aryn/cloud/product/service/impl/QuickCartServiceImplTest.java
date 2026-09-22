package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.entity.ShipSkuProfile;
import com.aryn.cloud.product.api.vo.QuickCartInfoVO;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import com.aryn.cloud.product.service.IShipProductProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuickCartServiceImplTest {

	private static final String TENANT_ID = "tenant-1";

	private GoodsSpuMapper goodsSpuMapper;

	private IShipProductProfileService shipProductProfileService;

	private QuickCartServiceImpl service;

	@BeforeEach
	void setUp() {
		ArynTenantContextHolder.setTenantId(TENANT_ID);
		goodsSpuMapper = mock(GoodsSpuMapper.class);
		shipProductProfileService = mock(IShipProductProfileService.class);
		service = new QuickCartServiceImpl(goodsSpuMapper, shipProductProfileService);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
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
	void singleSpecWithoutShipProfileUsesDefaultQuantityRule() {
		GoodsSpu spu = spu("spu-1", "0", List.of(sku("sku-1", 10, "0")));
		spu.setSpuUrls(new String[]{"https://cdn/spu.jpg"});
		when(goodsSpuMapper.selectApiSpuById("spu-1")).thenReturn(spu);
		when(shipProductProfileService.listSkuProfiles(anyString(), anyString())).thenReturn(List.of());

		QuickCartInfoVO info = service.getQuickCartInfo("spu-1");

		assertThat(info.getMode()).isEqualTo(QuickCartInfoVO.MODE_DIRECT);
		assertThat(info.getSkuId()).isEqualTo("sku-1");
		assertThat(info.getSalesPrice()).isEqualByComparingTo("10");
		assertThat(info.getStock()).isEqualTo(5);
		assertThat(info.getMoq()).isNull();
		assertThat(info.getStepQty()).isNull();
		// SKU 无图时回落到商品主图，避免加购弹层出现空图
		assertThat(info.getPicUrl()).isEqualTo("https://cdn/spu.jpg");
	}

	@Test
	void singleSpecCarriesShipProfileQuantityRule() {
		GoodsSpu spu = spu("spu-1", "0", List.of(sku("sku-1", 10, "0")));
		when(goodsSpuMapper.selectApiSpuById("spu-1")).thenReturn(spu);
		ShipSkuProfile profile = profile("sku-1", 6, 3, "箱", null);
		when(shipProductProfileService.listSkuProfiles(anyString(), anyString())).thenReturn(List.of(profile));

		QuickCartInfoVO info = service.getQuickCartInfo("spu-1");

		assertThat(info.getMode()).isEqualTo(QuickCartInfoVO.MODE_DIRECT);
		assertThat(info.getMoq()).isEqualTo(6);
		assertThat(info.getStepQty()).isEqualTo(3);
		assertThat(info.getPurchaseUnit()).isEqualTo("箱");
	}

	@Test
	void purchaseUnitFallsBackToBaseUnit() {
		GoodsSpu spu = spu("spu-1", "0", List.of(sku("sku-1", 10, "0")));
		when(goodsSpuMapper.selectApiSpuById("spu-1")).thenReturn(spu);
		when(shipProductProfileService.listSkuProfiles(anyString(), anyString()))
			.thenReturn(List.of(profile("sku-1", 2, 1, null, "瓶")));

		QuickCartInfoVO info = service.getQuickCartInfo("spu-1");

		assertThat(info.getPurchaseUnit()).isEqualTo("瓶");
	}

	@Test
	void multiSpecReturnsChooseWithSellableSkus() {
		GoodsSpu spu = spu("spu-1", "1", List.of(sku("sku-1", 10, "0"), sku("sku-2", 20, "0")));
		when(goodsSpuMapper.selectApiSpuById("spu-1")).thenReturn(spu);
		when(shipProductProfileService.listSkuProfiles(anyString(), anyString()))
			.thenReturn(List.of(profile("sku-2", 12, 6, "箱", null)));

		QuickCartInfoVO info = service.getQuickCartInfo("spu-1");

		assertThat(info.getMode()).isEqualTo(QuickCartInfoVO.MODE_CHOOSE);
		assertThat(info.getGoodsSkus()).hasSize(2);
		assertThat(info.getGoodsSkus())
			.filteredOn(item -> "sku-2".equals(item.getSkuId()))
			.singleElement()
			.satisfies(item -> {
				assertThat(item.getMoq()).isEqualTo(12);
				assertThat(item.getStepQty()).isEqualTo(6);
				assertThat(item.getPurchaseUnit()).isEqualTo("箱");
			});
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
		when(shipProductProfileService.listSkuProfiles(anyString(), anyString())).thenReturn(List.of());

		QuickCartInfoVO info = service.getQuickCartInfo("spu-1");

		assertThat(info.getSkuId()).isEqualTo("sku-big");
	}

	@Test
	void missingTenantSkipsShipProfileLookup() {
		ArynTenantContextHolder.removeTenantId();
		GoodsSpu spu = spu("spu-1", "0", List.of(sku("sku-1", 10, "0")));
		when(goodsSpuMapper.selectApiSpuById("spu-1")).thenReturn(spu);

		QuickCartInfoVO info = service.getQuickCartInfo("spu-1");

		assertThat(info.getMode()).isEqualTo(QuickCartInfoVO.MODE_DIRECT);
		verify(shipProductProfileService, never()).listSkuProfiles(any(), any());
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

	private ShipSkuProfile profile(String skuId, Integer moq, Integer stepQty, String purchaseUnit, String baseUnit) {
		ShipSkuProfile profile = new ShipSkuProfile();
		profile.setSkuId(skuId);
		profile.setMoq(moq);
		profile.setStepQty(stepQty);
		profile.setPurchaseUnit(purchaseUnit);
		profile.setBaseUnit(baseUnit);
		return profile;
	}

}
