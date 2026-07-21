package com.aryn.cloud.product.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.dto.GoodsSkuStockReqDTO;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.mapper.GoodsSkuMapper;
import com.aryn.cloud.product.service.IGoodsSpuService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GoodsSkuServiceImplTest {

	private GoodsSkuMapper mapper;

	private IGoodsSpuService goodsSpuService;

	private GoodsSkuServiceImpl service;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GoodsSku.class);
		mapper = mock(GoodsSkuMapper.class);
		goodsSpuService = mock(IGoodsSpuService.class);
		service = new TestGoodsSkuService(goodsSpuService, mapper);
	}

	@Test
	void reduceStockAggregatesDuplicateSkuAndRequiresEnoughStock() {
		when(mapper.update(any(GoodsSku.class), any(Wrapper.class))).thenReturn(1);
		when(goodsSpuService.reduceStock(any())).thenReturn(true);

		assertThat(service.reduceStock(List.of(stock("sku-1", "spu-1", 2), stock("sku-1", "spu-1", 3)))).isTrue();

		ArgumentCaptor<Wrapper<GoodsSku>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
		verify(mapper).update(any(GoodsSku.class), wrapperCaptor.capture());
		AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue();
		assertThat(wrapper.getSqlSegment()).contains("stock", "spu_id");
		assertThat(wrapper.getSqlSet()).contains("version = version + 1");
		assertThat(wrapper.getParamNameValuePairs()).containsValue("spu-1").containsValue(5);
		verify(goodsSpuService).reduceStock(Map.of("spu-1", 5));
	}

	@Test
	void rollbackStockIncrementsSkuVersion() {
		when(mapper.update(any(GoodsSku.class), any(Wrapper.class))).thenReturn(1);
		when(goodsSpuService.rollbackStock(any())).thenReturn(true);

		service.rollbackStockList(List.of(stock("sku-1", "spu-1", 2)));

		ArgumentCaptor<Wrapper<GoodsSku>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
		verify(mapper).update(any(GoodsSku.class), wrapperCaptor.capture());
		AbstractWrapper<?, ?, ?> wrapper = (AbstractWrapper<?, ?, ?>) wrapperCaptor.getValue();
		assertThat(wrapper.getSqlSegment()).contains("spu_id");
		assertThat(wrapper.getParamNameValuePairs()).containsValue("spu-1");
		assertThat(wrapper.getSqlSet()).contains("version = version + 1");
	}

	@Test
	void rollbackStockRestoresDeletedSkuWithoutIncreasingCurrentSpuStock() {
		when(mapper.update(any(GoodsSku.class), any(Wrapper.class))).thenReturn(0);
		when(mapper.restoreDeletedStock("sku-1", "spu-1", 2)).thenReturn(1);

		service.rollbackStockList(List.of(stock("sku-1", "spu-1", 2)));

		verify(mapper).restoreDeletedStock("sku-1", "spu-1", 2);
		verify(goodsSpuService, never()).rollbackStock(any());
	}

	@Test
	void rejectsNonPositiveStockChangeBeforeDatabaseWrite() {
		assertThatThrownBy(() -> service.reduceStock(List.of(stock("sku-1", "spu-1", 0))))
			.isInstanceOf(ArynBusinessException.class);
		verify(mapper, never()).update(any(), any());
		verify(goodsSpuService, never()).reduceStock(any());
	}

	@Test
	void rollbackDoesNotIncreaseSpuWhenSkuDoesNotExist() {
		when(mapper.update(any(GoodsSku.class), any(Wrapper.class))).thenReturn(0);
		when(mapper.restoreDeletedStock("missing", "spu-1", 2)).thenReturn(0);

		assertThatThrownBy(() -> service.rollbackStockList(List.of(stock("missing", "spu-1", 2))))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(exception -> assertThat(((ArynBusinessException) exception).getMsg()).isEqualTo("回滚SKU库存失败"));
		verify(goodsSpuService, never()).rollbackStock(any());
	}

	@Test
	void rollbackRejectsSkuThatDoesNotBelongToRequestedSpu() {
		when(mapper.update(any(GoodsSku.class), any(Wrapper.class))).thenReturn(0);
		when(mapper.restoreDeletedStock("sku-1", "wrong-spu", 2)).thenReturn(0);

		assertThatThrownBy(() -> service.rollbackStockList(List.of(stock("sku-1", "wrong-spu", 2))))
			.isInstanceOf(ArynBusinessException.class);

		verify(goodsSpuService, never()).rollbackStock(any());
	}

	private GoodsSkuStockReqDTO stock(String skuId, String spuId, int quantity) {
		GoodsSkuStockReqDTO request = new GoodsSkuStockReqDTO();
		request.setSkuId(skuId);
		request.setSpuId(spuId);
		request.setStockNum(quantity);
		return request;
	}

	private static final class TestGoodsSkuService extends GoodsSkuServiceImpl {

		private TestGoodsSkuService(IGoodsSpuService goodsSpuService, GoodsSkuMapper mapper) {
			super(goodsSpuService);
			this.baseMapper = mapper;
		}

	}

}
