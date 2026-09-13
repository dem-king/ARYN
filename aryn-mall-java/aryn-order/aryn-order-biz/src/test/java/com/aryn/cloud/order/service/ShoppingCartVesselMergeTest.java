package com.aryn.cloud.order.service;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.dto.ShoppingCartCreateDTO;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.order.api.entity.ShoppingCart;
import com.aryn.cloud.order.mapper.ShoppingCartMapper;
import com.aryn.cloud.order.service.impl.ShoppingCartServiceImpl;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 购物车防串船合并契约测试：同靠港合并数量，不同靠港各成一行。
 */
class ShoppingCartVesselMergeTest {

	private static final String TENANT = "tenant-1";

	private static final String USER = "user-1";

	private ShoppingCartMapper cartMapper;

	private RemoteGoodsSkuService remoteGoodsSkuService;

	private ShoppingCartServiceImpl service;

	@BeforeEach
	void setUp() {
		remoteGoodsSkuService = mock(RemoteGoodsSkuService.class);
		ShoppingCartServiceImpl impl = new ShoppingCartServiceImpl(remoteGoodsSkuService);
		cartMapper = mock(ShoppingCartMapper.class);
		ReflectionTestUtils.setField(impl, "baseMapper", cartMapper);
		service = org.mockito.Mockito.spy(impl);
		ArynTenantContextHolder.setTenantId(TENANT);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	private void stubSaleSku() {
		GoodsSku sku = new GoodsSku();
		sku.setId("sku-1");
		sku.setSalesPrice(BigDecimal.TEN);
		sku.setStock(100);
		sku.setStatus("1");
		GoodsSpu spu = new GoodsSpu();
		spu.setId("spu-1");
		spu.setName("测试商品");
		sku.setGoodsSpu(spu);
		when(remoteGoodsSkuService.getBySkuIds(List.of("sku-1"))).thenReturn(List.of(sku));
	}

	private ShoppingCartCreateDTO request(String callId) {
		ShoppingCartCreateDTO dto = new ShoppingCartCreateDTO();
		dto.setSkuId("sku-1");
		dto.setQuantity(5);
		dto.setVesselId("vessel-1");
		dto.setVesselCallId(callId);
		dto.setPurchaseScene("2");
		return dto;
	}

	private ShoppingCart existingRow(String id, String callId, int quantity) {
		ShoppingCart row = new ShoppingCart();
		row.setId(id);
		row.setUserId(USER);
		row.setSkuId("sku-1");
		row.setVesselId("vessel-1");
		row.setVesselCallId(callId);
		row.setQuantity(quantity);
		return row;
	}

	@Test
	@DisplayName("同用户同SKU同靠港命中可合并行：数量累加，不新建")
	void sameCallMergesIntoExistingRow() {
		stubSaleSku();
		when(cartMapper.selectOne(any())).thenReturn(existingRow("row-1", "call-1", 3));
		when(cartMapper.incrementQuantityById(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
				.thenReturn(1);

		boolean result = service.saveShoppingCart(USER, request("call-1"));

		assertTrue(result);
		ArgumentCaptor<String> idCaptor = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<Integer> qtyCaptor = ArgumentCaptor.forClass(Integer.class);
		verify(cartMapper).incrementQuantityById(org.mockito.ArgumentMatchers.eq(USER), idCaptor.capture(),
				qtyCaptor.capture());
		assertEquals("row-1", idCaptor.getValue());
		assertEquals(5, qtyCaptor.getValue());
		verify(service, never()).save(any(ShoppingCart.class));
	}

	@Test
	@DisplayName("不同靠港不合并：新建归属行")
	void differentCallCreatesNewRow() {
		stubSaleSku();
		when(cartMapper.selectOne(any())).thenReturn(null);
		org.mockito.Mockito.doReturn(true).when(service).save(any(ShoppingCart.class));

		boolean result = service.saveShoppingCart(USER, request("call-2"));

		assertTrue(result);
		ArgumentCaptor<ShoppingCart> saveCaptor = ArgumentCaptor.forClass(ShoppingCart.class);
		verify(service).save(saveCaptor.capture());
		assertEquals("call-2", saveCaptor.getValue().getVesselCallId());
		assertEquals("vessel-1", saveCaptor.getValue().getVesselId());
		assertEquals("2", saveCaptor.getValue().getPurchaseScene());
		verify(cartMapper, never()).incrementQuantityById(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt());
	}

	@Test
	@DisplayName("无上下文加购归入未指定组，与有靠港行互不合并")
	void nullCallIsolatesFromVesselRows() {
		stubSaleSku();
		when(cartMapper.selectOne(any())).thenReturn(null);
		org.mockito.Mockito.doReturn(true).when(service).save(any(ShoppingCart.class));

		service.saveShoppingCart(USER, request(null));

		ArgumentCaptor<ShoppingCart> saveCaptor = ArgumentCaptor.forClass(ShoppingCart.class);
		verify(service).save(saveCaptor.capture());
		org.junit.jupiter.api.Assertions.assertNull(saveCaptor.getValue().getVesselCallId());
	}

	@Test
	@DisplayName("并发命中唯一键时兜底合并同靠港行")
	void duplicateKeyFallsBackToMerge() {
		stubSaleSku();
		when(cartMapper.selectOne(any())).thenReturn(null, existingRow("row-2", "call-1", 1));
		when(cartMapper.incrementQuantityById(anyString(), anyString(), org.mockito.ArgumentMatchers.anyInt()))
				.thenReturn(1);
		when(service.save(any(ShoppingCart.class))).thenThrow(new DuplicateKeyException("uk"));

		boolean result = service.saveShoppingCart(USER, request("call-1"));

		assertTrue(result);
		verify(cartMapper).incrementQuantityById(org.mockito.ArgumentMatchers.eq(USER),
				org.mockito.ArgumentMatchers.eq("row-2"), org.mockito.ArgumentMatchers.eq(5));
	}

}
