package com.aryn.cloud.order.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.ShoppingCartCreateDTO;
import com.aryn.cloud.order.api.dto.ShoppingCartUpdateDTO;
import com.aryn.cloud.order.api.entity.ShoppingCart;
import com.aryn.cloud.order.mapper.ShoppingCartMapper;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShoppingCartServiceImplTest {

	private ShoppingCartMapper mapper;
	private RemoteGoodsSkuService remoteGoodsSkuService;
	private ShoppingCartServiceImpl service;

	@BeforeEach
	void setUp() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ShoppingCart.class);
		mapper = mock(ShoppingCartMapper.class);
		remoteGoodsSkuService = mock(RemoteGoodsSkuService.class);
		service = new TestShoppingCartService(remoteGoodsSkuService, mapper);
	}

	@Test
	void addUsesServerSideSkuSnapshot() {
		when(remoteGoodsSkuService.getBySkuIds(List.of("sku-1"))).thenReturn(List.of(saleSku(10)));
		when(mapper.incrementQuantity("user-1", "sku-1", 2)).thenReturn(0);
		when(mapper.insert(any(ShoppingCart.class))).thenReturn(1);
		ShoppingCartCreateDTO request = new ShoppingCartCreateDTO();
		request.setSkuId("sku-1");
		request.setQuantity(2);

		assertThat(service.saveShoppingCart("user-1", request)).isTrue();

		ArgumentCaptor<ShoppingCart> captor = ArgumentCaptor.forClass(ShoppingCart.class);
		verify(mapper).insert(captor.capture());
		ShoppingCart saved = captor.getValue();
		assertThat(saved.getUserId()).isEqualTo("user-1");
		assertThat(saved.getSpuId()).isEqualTo("spu-1");
		assertThat(saved.getSpuName()).isEqualTo("服务端商品");
		assertThat(saved.getSalesPrice()).isEqualByComparingTo("19.90");
		assertThat(saved.getPicUrl()).isEqualTo("sku.png");
		assertThat(saved.getSpecsInfo()).isEqualTo("红色");
	}

	@Test
	void quantityUpdateRevalidatesStockBeforeWriting() {
		ShoppingCart source = new ShoppingCart();
		source.setId("cart-1");
		source.setUserId("user-1");
		source.setSkuId("sku-1");
		when(mapper.selectOne(any(Wrapper.class))).thenReturn(source);
		when(remoteGoodsSkuService.getBySkuIds(List.of("sku-1"))).thenReturn(List.of(saleSku(1)));
		ShoppingCartUpdateDTO request = new ShoppingCartUpdateDTO();
		request.setId("cart-1");
		request.setQuantity(2);

		assertThatThrownBy(() -> service.updateShoppingCart("user-1", request))
			.isInstanceOf(ArynBusinessException.class);
		verify(mapper, never()).updateById(any(ShoppingCart.class));
	}

	@Test
	void quantityUpdateRefreshesSnapshot() {
		ShoppingCart source = new ShoppingCart();
		source.setId("cart-1");
		source.setUserId("user-1");
		source.setSkuId("sku-1");
		when(mapper.selectOne(any(Wrapper.class))).thenReturn(source);
		when(remoteGoodsSkuService.getBySkuIds(List.of("sku-1"))).thenReturn(List.of(saleSku(10)));
		when(mapper.updateById(any(ShoppingCart.class))).thenReturn(1);
		ShoppingCartUpdateDTO request = new ShoppingCartUpdateDTO();
		request.setId("cart-1");
		request.setQuantity(3);

		assertThat(service.updateShoppingCart("user-1", request)).isTrue();

		ArgumentCaptor<ShoppingCart> captor = ArgumentCaptor.forClass(ShoppingCart.class);
		verify(mapper).updateById(captor.capture());
		assertThat(captor.getValue().getQuantity()).isEqualTo(3);
		assertThat(captor.getValue().getSpuName()).isEqualTo("服务端商品");
		assertThat(captor.getValue().getSalesPrice()).isEqualByComparingTo("19.90");
	}

	private GoodsSku saleSku(int stock) {
		GoodsSpu spu = new GoodsSpu().setId("spu-1").setName("服务端商品")
			.setStatus("1").setSpuUrls(new String[] { "spu.png" });
		return new GoodsSku().setId("sku-1").setSpuId("spu-1").setStock(stock)
			.setSalesPrice(new BigDecimal("19.90")).setPicUrl("sku.png")
			.setSpecsArr(List.of(new GoodsSku.Specs("spec-1", "颜色", "value-1", "红色")))
			.setGoodsSpu(spu);
	}

	private static final class TestShoppingCartService extends ShoppingCartServiceImpl {

		private TestShoppingCartService(RemoteGoodsSkuService remoteGoodsSkuService, ShoppingCartMapper mapper) {
			super(remoteGoodsSkuService);
			this.baseMapper = mapper;
		}
	}
}
