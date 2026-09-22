package com.aryn.cloud.order.service;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.api.vo.SharedCartSummaryVO;
import com.aryn.cloud.order.mapper.SharedCartItemMapper;
import com.aryn.cloud.order.mapper.SharedCartMapper;
import com.aryn.cloud.order.mapper.SharedCartMemberMapper;
import com.aryn.cloud.order.service.impl.SharedCartServiceImpl;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.product.api.remote.RemoteShipProductProfileService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 首页「今日补给单」摘要契约测试。
 *
 * <p>重点守两件事：① 无进行中购物车时返回零值而非抛异常（首页要能渲染空态）；
 * ② 合计金额与预览截断的口径正确，不能虚报。
 */
class SharedCartSummaryTest {

	private static final String TENANT = "tenant-1";

	private static final String USER = "user-1";

	private static final String CART_ID = "cart-1";

	private SharedCartMapper cartMapper;

	private SharedCartMemberMapper memberMapper;

	private SharedCartItemMapper itemMapper;

	private RemoteGoodsSkuService remoteGoodsSkuService;

	private SharedCartServiceImpl service;

	@BeforeEach
	void setUp() {
		cartMapper = mock(SharedCartMapper.class);
		memberMapper = mock(SharedCartMemberMapper.class);
		itemMapper = mock(SharedCartItemMapper.class);
		remoteGoodsSkuService = mock(RemoteGoodsSkuService.class);
		// lambdaUpdate/lambdaQuery 需要实体表信息缓存，否则单测中抛 lambda cache 异常
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SharedCart.class);

		service = new SharedCartServiceImpl(cartMapper, memberMapper, itemMapper,
				mock(IOrderInfoService.class));
		ReflectionTestUtils.setField(service, "remoteGoodsSkuService", remoteGoodsSkuService);
		ReflectionTestUtils.setField(service, "remoteShipProductProfileService",
				mock(RemoteShipProductProfileService.class));
		ReflectionTestUtils.setField(service, "remoteVesselService", mock(RemoteVesselService.class));
		ReflectionTestUtils.setField(service, "remoteMallUserService", mock(RemoteMallUserService.class));
		ArynTenantContextHolder.setTenantId(TENANT);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	private SharedCartMember membership() {
		SharedCartMember member = new SharedCartMember();
		member.setCartId(CART_ID);
		member.setUserId(USER);
		member.setMemberRole(SharedCartMember.ROLE_OWNER);
		return member;
	}

	private SharedCart collectingCart() {
		SharedCart cart = new SharedCart();
		cart.setId(CART_ID);
		cart.setCartNo("SC1");
		cart.setTenantId(TENANT);
		cart.setVesselId("vessel-1");
		cart.setVesselCallId("call-1");
		cart.setOwnerUserId(USER);
		cart.setConfirmerUserId(USER);
		cart.setStatus(SharedCart.STATUS_COLLECTING);
		cart.setExpiresAt(LocalDateTime.now().plusHours(12));
		return cart;
	}

	private SharedCartItem item(String itemId, String skuId, int qty) {
		SharedCartItem item = new SharedCartItem();
		item.setId(itemId);
		item.setCartId(CART_ID);
		item.setUserId(USER);
		item.setSpuId("spu-" + skuId);
		item.setSkuId(skuId);
		item.setRequestedQuantity(qty);
		item.setStatus(SharedCartItem.ITEM_PENDING);
		return item;
	}

	private GoodsSku sku(String skuId, String price) {
		GoodsSku sku = new GoodsSku();
		sku.setId(skuId);
		sku.setSalesPrice(new BigDecimal(price));
		GoodsSpu spu = new GoodsSpu();
		spu.setId("spu-" + skuId);
		spu.setName("商品" + skuId);
		sku.setGoodsSpu(spu);
		return sku;
	}

	@Test
	@DisplayName("无进行中的购物车：返回零值且 cart 为 null，不抛异常")
	void returnsEmptyWhenNoActiveCart() {
		when(memberMapper.selectList(any())).thenReturn(List.of());

		SharedCartSummaryVO summary = service.getActiveSummary(TENANT, USER, null);

		assertNull(summary.getCart());
		assertEquals(0, summary.getItemCount());
		assertEquals(BigDecimal.ZERO, summary.getTotalAmount());
		assertTrue(summary.getPreviewItems().isEmpty());
		assertFalse(summary.getPreviewTruncated());
	}

	@Test
	@DisplayName("有进行中购物车：统计项数、人数并按售价×数量算合计")
	void aggregatesCountsAndAmount() {
		when(memberMapper.selectList(any())).thenReturn(List.of(membership()));
		when(cartMapper.selectOne(any())).thenReturn(collectingCart());
		when(itemMapper.selectList(any()))
				.thenReturn(List.of(item("i1", "sku-1", 2), item("i2", "sku-2", 3)));
		when(remoteGoodsSkuService.getSkuByIds(anyList()))
				.thenReturn(List.of(sku("sku-1", "10.00"), sku("sku-2", "5.50")));

		SharedCartSummaryVO summary = service.getActiveSummary(TENANT, USER, null);

		assertNotNull(summary.getCart());
		assertEquals(2, summary.getItemCount());
		// 2*10.00 + 3*5.50 = 36.50
		assertEquals(0, new BigDecimal("36.50").compareTo(summary.getTotalAmount()));
		assertEquals(2, summary.getPreviewItems().size());
		assertEquals("商品sku-1", summary.getPreviewItems().get(0).getSpuName());
	}

	@Test
	@DisplayName("明细超过预言上限：预览截断为 3 条并标记 truncated")
	void truncatesPreview() {
		when(memberMapper.selectList(any())).thenReturn(List.of(membership()));
		when(cartMapper.selectOne(any())).thenReturn(collectingCart());
		when(itemMapper.selectList(any())).thenReturn(List.of(
				item("i1", "sku-1", 1), item("i2", "sku-2", 1),
				item("i3", "sku-3", 1), item("i4", "sku-4", 1)));
		when(remoteGoodsSkuService.getSkuByIds(anyList())).thenReturn(List.of(
				sku("sku-1", "1"), sku("sku-2", "1"), sku("sku-3", "1"), sku("sku-4", "1")));

		SharedCartSummaryVO summary = service.getActiveSummary(TENANT, USER, null);

		assertEquals(4, summary.getItemCount());
		assertEquals(3, summary.getPreviewItems().size());
		assertTrue(summary.getPreviewTruncated());
	}

	@Test
	@DisplayName("商品域不可用：降级返回项数与零价，不因商品信息缺失整张卡消失")
	void degradesWhenGoodsServiceFails() {
		when(memberMapper.selectList(any())).thenReturn(List.of(membership()));
		when(cartMapper.selectOne(any())).thenReturn(collectingCart());
		when(itemMapper.selectList(any())).thenReturn(List.of(item("i1", "sku-1", 2)));
		when(remoteGoodsSkuService.getSkuByIds(anyList()))
				.thenThrow(new RuntimeException("dubbo down"));

		SharedCartSummaryVO summary = service.getActiveSummary(TENANT, USER, null);

		assertNotNull(summary.getCart());
		assertEquals(1, summary.getItemCount());
		assertEquals(0, BigDecimal.ZERO.compareTo(summary.getTotalAmount()));
		// 明细仍要返回，供前端用 SKU ID 兜底展示
		assertEquals(1, summary.getPreviewItems().size());
		assertEquals("sku-1", summary.getPreviewItems().get(0).getSkuId());
	}

	@Test
	@DisplayName("明细为空：仍返回购物车本体，金额为零")
	void emptyCartStillReturnsCart() {
		when(memberMapper.selectList(any())).thenReturn(List.of(membership()));
		when(cartMapper.selectOne(any())).thenReturn(collectingCart());
		when(itemMapper.selectList(any())).thenReturn(List.of());

		SharedCartSummaryVO summary = service.getActiveSummary(TENANT, USER, null);

		assertNotNull(summary.getCart());
		assertEquals(0, summary.getItemCount());
		assertEquals(BigDecimal.ZERO, summary.getTotalAmount());
	}

}
