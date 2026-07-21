package com.aryn.cloud.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.mapper.GoodsCategoryMapper;
import com.aryn.cloud.product.mapper.GoodsCollectMapper;
import com.aryn.cloud.product.mapper.GoodsFootprintMapper;
import com.aryn.cloud.product.mapper.GoodsSkuMapper;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoodsSpuServiceImplTest {

	private GoodsSpuMapper spuMapper;

	private GoodsSkuMapper skuMapper;

	private GoodsSpuServiceImpl service;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GoodsSpu.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GoodsSku.class);
		spuMapper = mock(GoodsSpuMapper.class);
		skuMapper = mock(GoodsSkuMapper.class);
		service = new TestGoodsSpuService(skuMapper, mock(GoodsCollectMapper.class), mock(GoodsFootprintMapper.class),
				mock(GoodsCategoryMapper.class), spuMapper);
	}

	@Test
	void saveGoodsCalculatesPricesAndClearsServerOwnedFields() {
		when(spuMapper.insert(any(GoodsSpu.class))).thenAnswer(invocation -> {
			GoodsSpu saved = invocation.getArgument(0);
			saved.setId("spu-1");
			return 1;
		});
		when(skuMapper.insert(any(GoodsSku.class))).thenReturn(1);
		GoodsSku firstSku = sku(null, 20, 100, 60, 3, 0);
		firstSku.setSpecsArr(List.of(new GoodsSku.Specs("spec-color", "颜色", "red", "红色")));
		GoodsSku secondSku = sku(null, 30, 80, 40, 5, 0);
		secondSku.setSpecsArr(List.of(new GoodsSku.Specs("spec-color", "颜色", "blue", "蓝色")));
		GoodsSpu request = product(null, List.of(firstSku, secondSku));
		request.setId("client-spu-id");
		request.setTenantId("victim-tenant");
		request.setSalesVolume(999);
		request.getGoodsSkus().forEach(item -> item.setTenantId("victim-tenant"));

		assertThat(service.saveGoods(request)).isTrue();

		assertThat(request.getId()).isEqualTo("spu-1");
		assertThat(request.getTenantId()).isNull();
		assertThat(request.getSalesVolume()).isZero();
		assertThat(request.getSalesPrice()).isEqualByComparingTo("20");
		assertThat(request.getOriginalPrice()).isEqualByComparingTo("80");
		assertThat(request.getCostPrice()).isEqualByComparingTo("40");
		assertThat(request.getStock()).isEqualTo(8);
		assertThat(request.getGoodsSkus()).allSatisfy(item -> {
			assertThat(item.getTenantId()).isNull();
			assertThat(item.getSpuId()).isEqualTo("spu-1");
		});
	}

	@Test
	void saveGoodsRejectsEmptySkuList() {
		GoodsSpu request = product(null, List.of());

		assertThatThrownBy(() -> service.saveGoods(request)).isInstanceOf(ArynBusinessException.class)
			.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg()).contains("SKU"));
		verify(spuMapper, never()).insert(any(GoodsSpu.class));
	}

	@Test
	void saveGoodsRejectsDuplicateSpecificationCombination() {
		GoodsSku first = sku(null, 10, 10, 8, 1, 0);
		GoodsSku second = sku(null, 12, 12, 9, 1, 0);
		GoodsSku.Specs red = new GoodsSku.Specs("spec-color", "颜色", "red", "红色");
		first.setSpecsArr(List.of(red));
		second.setSpecsArr(List.of(red));

		assertThatThrownBy(() -> service.saveGoods(product(null, List.of(first, second))))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg()).contains("重复"));
		verify(spuMapper, never()).insert(any(GoodsSpu.class));
	}

	@Test
	void saveGoodsRejectsTotalStockOverflow() {
		GoodsSku first = sku(null, 10, 10, 8, Integer.MAX_VALUE, 0);
		first.setSpecsArr(List.of(new GoodsSku.Specs("spec-color", "颜色", "red", "红色")));
		GoodsSku second = sku(null, 12, 12, 9, 1, 0);
		second.setSpecsArr(List.of(new GoodsSku.Specs("spec-color", "颜色", "blue", "蓝色")));

		assertThatThrownBy(() -> service.saveGoods(product(null, List.of(first, second))))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg()).contains("库存总量"));
		verify(spuMapper, never()).insert(any(GoodsSpu.class));
	}

	@Test
	void updateGoodsRejectsSkuOwnedByAnotherProduct() {
		GoodsSpu existing = product("spu-1", List.of());
		when(spuMapper.selectById("spu-1")).thenReturn(existing);
		when(skuMapper.selectBySpuId("spu-1")).thenReturn(List.of(sku("sku-1", 10, 10, 8, 5, 2)));
		GoodsSpu request = product("spu-1", List.of(sku("sku-other", 10, 10, 8, 5, 1)));

		assertThatThrownBy(() -> service.updateGoods(request)).isInstanceOf(ArynBusinessException.class)
			.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg()).contains("不属于当前商品"));
		verify(skuMapper, never()).updateById(any(GoodsSku.class));
	}

	@Test
	void updateGoodsRejectsStaleSkuVersion() {
		GoodsSpu existing = product("spu-1", List.of());
		when(spuMapper.selectById("spu-1")).thenReturn(existing);
		when(skuMapper.selectBySpuId("spu-1")).thenReturn(List.of(sku("sku-1", 10, 10, 8, 5, 3)));
		GoodsSpu request = product("spu-1", List.of(sku("sku-1", 10, 10, 8, 6, 2)));

		assertThatThrownBy(() -> service.updateGoods(request)).isInstanceOf(ArynBusinessException.class)
			.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg()).contains("库存已变化"));
		verify(skuMapper, never()).updateById(any(GoodsSku.class));
	}

	@Test
	void updateGoodsAppliesStockDeltaInsteadOfOverwritingAggregateStock() {
		GoodsSpu existing = product("spu-1", List.of());
		existing.setStock(5);
		GoodsSku storedSku = sku("sku-1", 10, 10, 8, 5, 2);
		when(spuMapper.selectById("spu-1")).thenReturn(existing);
		when(skuMapper.selectBySpuId("spu-1")).thenReturn(List.of(storedSku));
		when(skuMapper.updateById(any(GoodsSku.class))).thenReturn(1);
		when(spuMapper.updateById(any(GoodsSpu.class))).thenReturn(1);
		when(spuMapper.update(any(GoodsSpu.class), any(Wrapper.class))).thenReturn(1);
		GoodsSpu request = product("spu-1", List.of(sku("sku-1", 12, 15, 9, 8, 2)));
		request.setStock(999);
		request.setTenantId("victim-tenant");

		assertThat(service.updateGoods(request)).isTrue();

		assertThat(request.getStock()).isNull();
		assertThat(request.getTenantId()).isNull();
		verify(spuMapper).update(any(GoodsSpu.class), any(Wrapper.class));
	}

	private GoodsSpu product(String id, List<GoodsSku> skus) {
		GoodsSpu product = new GoodsSpu();
		product.setId(id);
		product.setName("测试商品");
		product.setSpuUrls(new String[] { "image.jpg" });
		product.setStatus("1");
		product.setEnableSpecs(skus.size() > 1 ? "1" : "0");
		product.setCategoryFirstId("category-1");
		product.setCategorySecondId("category-2");
		product.setFreightType("0");
		product.setGoodsSkus(skus);
		return product;
	}

	private GoodsSku sku(String id, int salesPrice, int originalPrice, int costPrice, int stock, int version) {
		GoodsSku sku = new GoodsSku();
		sku.setId(id);
		sku.setSalesPrice(BigDecimal.valueOf(salesPrice));
		sku.setOriginalPrice(BigDecimal.valueOf(originalPrice));
		sku.setCostPrice(BigDecimal.valueOf(costPrice));
		sku.setStock(stock);
		sku.setStatus("0");
		sku.setVersion(version);
		return sku;
	}

	private static final class TestGoodsSpuService extends GoodsSpuServiceImpl {

		private TestGoodsSpuService(GoodsSkuMapper goodsSkuMapper, GoodsCollectMapper goodsCollectMapper,
				GoodsFootprintMapper goodsFootprintMapper, GoodsCategoryMapper goodsCategoryMapper,
				GoodsSpuMapper goodsSpuMapper) {
			super(goodsSkuMapper, goodsCollectMapper, goodsFootprintMapper, goodsCategoryMapper);
			this.baseMapper = goodsSpuMapper;
		}

	}

}
