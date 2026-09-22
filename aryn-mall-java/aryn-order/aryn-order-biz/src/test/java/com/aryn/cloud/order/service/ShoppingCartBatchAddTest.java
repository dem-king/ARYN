package com.aryn.cloud.order.service;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.ShoppingCartBatchAddDTO;
import com.aryn.cloud.order.api.dto.ShoppingCartCreateDTO;
import com.aryn.cloud.order.api.entity.ShoppingCart;
import com.aryn.cloud.order.api.vo.ShoppingCartBatchAddVO;
import com.aryn.cloud.order.mapper.ShoppingCartMapper;
import com.aryn.cloud.order.service.impl.ShoppingCartServiceImpl;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.entity.ShipSkuProfile;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.product.api.remote.RemoteShipProductProfileService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 购物车批量加购契约测试。
 *
 * <p>核心语义是**部分成功**：批量场景多来自「勾选清单里的多项一次加入」，
 * 若其中一项缺货/不满足 MOQ 就整批失败，用户只能反复试错找出是哪一项。
 */
class ShoppingCartBatchAddTest {

	private static final String TENANT = "tenant-1";

	private static final String USER = "user-1";

	private ShoppingCartMapper cartMapper;

	private RemoteGoodsSkuService remoteGoodsSkuService;

	private RemoteShipProductProfileService remoteShipProductProfileService;

	private ShoppingCartServiceImpl service;

	@BeforeEach
	void setUp() {
		remoteGoodsSkuService = mock(RemoteGoodsSkuService.class);
		remoteShipProductProfileService = mock(RemoteShipProductProfileService.class);
		ShoppingCartServiceImpl impl = new ShoppingCartServiceImpl(remoteGoodsSkuService);
		cartMapper = mock(ShoppingCartMapper.class);
		ReflectionTestUtils.setField(impl, "baseMapper", cartMapper);
		ReflectionTestUtils.setField(impl, "remoteShipProductProfileService", remoteShipProductProfileService);
		service = spy(impl);
		ArynTenantContextHolder.setTenantId(TENANT);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	private void stubSaleSku(String skuId, int stock) {
		GoodsSku sku = new GoodsSku();
		sku.setId(skuId);
		sku.setSalesPrice(BigDecimal.TEN);
		sku.setStock(stock);
		sku.setStatus("1");
		GoodsSpu spu = new GoodsSpu();
		spu.setId("spu-" + skuId);
		spu.setName("商品" + skuId);
		sku.setGoodsSpu(spu);
		when(remoteGoodsSkuService.getBySkuIds(List.of(skuId))).thenReturn(List.of(sku));
	}

	private ShoppingCartCreateDTO item(String skuId, int quantity) {
		ShoppingCartCreateDTO dto = new ShoppingCartCreateDTO();
		dto.setSkuId(skuId);
		dto.setQuantity(quantity);
		dto.setVesselId("vessel-1");
		dto.setVesselCallId("call-1");
		dto.setPurchaseScene("2");
		return dto;
	}

	private ShoppingCartBatchAddDTO request(ShoppingCartCreateDTO... items) {
		ShoppingCartBatchAddDTO dto = new ShoppingCartBatchAddDTO();
		dto.setItems(new ArrayList<>(List.of(items)));
		return dto;
	}

	private ShipSkuProfile profile(String skuId, int moq, int stepQty) {
		ShipSkuProfile profile = new ShipSkuProfile();
		profile.setSkuId(skuId);
		profile.setMoq(moq);
		profile.setStepQty(stepQty);
		return profile;
	}

	@Test
	@DisplayName("全部成功：逐项写入，失败明细为空")
	void allSucceed() {
		stubSaleSku("sku-1", 100);
		stubSaleSku("sku-2", 100);
		when(cartMapper.selectOne(any())).thenReturn(null);
		doReturn(true).when(service).save(any(ShoppingCart.class));
		when(remoteShipProductProfileService.getSkuProfiles(anyString(), anyList())).thenReturn(List.of());

		ShoppingCartBatchAddVO result = service.batchAdd(USER,
				request(item("sku-1", 2), item("sku-2", 3)));

		assertEquals(2, result.getRequestedCount());
		assertEquals(2, result.getAddedCount());
		assertEquals(0, result.getFailedCount());
		assertTrue(result.getFailures().isEmpty());
	}

	@Test
	@DisplayName("部分失败：一项库存不足不回滚其余项，失败项带可读原因")
	void partialFailureKeepsSuccessfulItems() {
		stubSaleSku("sku-1", 100);
		// sku-2 请求 5 件但库存只有 1 件 -> requireSaleSku 抛业务异常
		stubSaleSku("sku-2", 1);
		when(cartMapper.selectOne(any())).thenReturn(null);
		doReturn(true).when(service).save(any(ShoppingCart.class));
		when(remoteShipProductProfileService.getSkuProfiles(anyString(), anyList())).thenReturn(List.of());

		ShoppingCartBatchAddVO result = service.batchAdd(USER,
				request(item("sku-1", 2), item("sku-2", 5)));

		assertEquals(2, result.getRequestedCount());
		assertEquals(1, result.getAddedCount());
		assertEquals(1, result.getFailedCount());
		assertEquals("sku-2", result.getFailures().get(0).getSkuId());
		assertEquals(5, result.getFailures().get(0).getQuantity());
		// 失败原因必须是非空可读文案，不能把内部异常类名透给用户
		assertTrue(result.getFailures().get(0).getReason() != null
				&& !result.getFailures().get(0).getReason().isBlank());
	}

	@Test
	@DisplayName("低于 MOQ：拦截并给出最小起订量提示")
	void rejectsBelowMoq() {
		stubSaleSku("sku-1", 100);
		when(remoteShipProductProfileService.getSkuProfiles(anyString(), anyList()))
				.thenReturn(List.of(profile("sku-1", 10, 1)));

		ShoppingCartBatchAddVO result = service.batchAdd(USER, request(item("sku-1", 3)));

		assertEquals(0, result.getAddedCount());
		assertEquals(1, result.getFailedCount());
		assertTrue(result.getFailures().get(0).getReason().contains("最小起订量"));
		// 数量规则不通过时不应落库
		verify(service, never()).save(any(ShoppingCart.class));
	}

	@Test
	@DisplayName("不满足步长：拦截并给出步长提示")
	void rejectsWrongStep() {
		stubSaleSku("sku-1", 100);
		when(remoteShipProductProfileService.getSkuProfiles(anyString(), anyList()))
				.thenReturn(List.of(profile("sku-1", 1, 6)));

		ShoppingCartBatchAddVO result = service.batchAdd(USER, request(item("sku-1", 7)));

		assertEquals(0, result.getAddedCount());
		assertEquals(1, result.getFailedCount());
		assertTrue(result.getFailures().get(0).getReason().contains("整数倍"));
		verify(service, never()).save(any(ShoppingCart.class));
	}

	@Test
	@DisplayName("无船供资料的商品不做 MOQ/步长校验，按普通商品放行")
	void skipsRuleWhenNoProfile() {
		stubSaleSku("sku-1", 100);
		when(cartMapper.selectOne(any())).thenReturn(null);
		doReturn(true).when(service).save(any(ShoppingCart.class));
		// 商品没有包装资料 -> profiles 为空
		when(remoteShipProductProfileService.getSkuProfiles(anyString(), anyList())).thenReturn(List.of());

		ShoppingCartBatchAddVO result = service.batchAdd(USER, request(item("sku-1", 7)));

		assertEquals(1, result.getAddedCount());
	}

	@Test
	@DisplayName("包装资料远程失败时降级：不因装饰性规则打挂整批加购")
	void degradesWhenProfileServiceFails() {
		stubSaleSku("sku-1", 100);
		when(cartMapper.selectOne(any())).thenReturn(null);
		doReturn(true).when(service).save(any(ShoppingCart.class));
		when(remoteShipProductProfileService.getSkuProfiles(anyString(), anyList()))
				.thenThrow(new ArynBusinessException("远程不可用"));

		ShoppingCartBatchAddVO result = service.batchAdd(USER, request(item("sku-1", 7)));

		assertEquals(1, result.getAddedCount());
		assertEquals(0, result.getFailedCount());
	}

	@Test
	@DisplayName("落库返回 false 也计入失败明细，成功数不虚高")
	void countsSaveFailure() {
		stubSaleSku("sku-1", 100);
		when(cartMapper.selectOne(any())).thenReturn(null);
		doReturn(false).when(service).save(any(ShoppingCart.class));
		when(remoteShipProductProfileService.getSkuProfiles(anyString(), anyList())).thenReturn(List.of());

		ShoppingCartBatchAddVO result = service.batchAdd(USER, request(item("sku-1", 2)));

		assertEquals(0, result.getAddedCount());
		assertEquals(1, result.getFailedCount());
		assertEquals("sku-1", result.getFailures().get(0).getSkuId());
	}

}
