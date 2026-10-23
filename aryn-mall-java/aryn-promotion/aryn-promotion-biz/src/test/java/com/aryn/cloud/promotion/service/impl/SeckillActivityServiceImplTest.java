package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.remote.RemoteShoppingCartService;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.aryn.cloud.promotion.api.entity.SeckillActivity;
import com.aryn.cloud.promotion.api.entity.SeckillGoods;
import com.aryn.cloud.promotion.api.entity.SeckillSession;
import com.aryn.cloud.promotion.api.vo.AppSeckillVO;
import com.aryn.cloud.promotion.api.vo.SeckillActivityVO;
import com.aryn.cloud.promotion.api.vo.SeckillGoodsVO;
import com.aryn.cloud.promotion.mapper.SeckillActivityMapper;
import com.aryn.cloud.promotion.service.ISeckillGoodsService;
import com.aryn.cloud.promotion.service.ISeckillSessionService;
import com.aryn.cloud.promotion.service.SeckillStockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 管理端秒杀活动详情：商品名称/主图回填（seckill_goods 只存 SPU/SKU ID）。
 */
class SeckillActivityServiceImplTest {

	private ISeckillSessionService seckillSessionService;

	private ISeckillGoodsService seckillGoodsService;

	private RemoteGoodsSpuService remoteGoodsSpuService;

	private RemoteShoppingCartService remoteShoppingCartService;

	private SeckillStockManager seckillStockManager;

	private RemoteGoodsSkuService remoteGoodsSkuService;

	private SeckillActivityMapper activityMapper;

	private SeckillActivityServiceImpl service;

	@BeforeEach
	void setUp() {
		seckillSessionService = mock(ISeckillSessionService.class);
		seckillGoodsService = mock(ISeckillGoodsService.class);
		remoteGoodsSpuService = mock(RemoteGoodsSpuService.class);
		remoteShoppingCartService = mock(RemoteShoppingCartService.class);
		seckillStockManager = mock(SeckillStockManager.class);
		remoteGoodsSkuService = mock(RemoteGoodsSkuService.class);
		activityMapper = mock(SeckillActivityMapper.class);
		service = new SeckillActivityServiceImpl(seckillSessionService, seckillGoodsService,
				seckillStockManager, remoteGoodsSkuService);
		ReflectionTestUtils.setField(service, "remoteGoodsSpuService", remoteGoodsSpuService);
		ReflectionTestUtils.setField(service, "remoteShoppingCartService", remoteShoppingCartService);
		ReflectionTestUtils.setField(service, "baseMapper", activityMapper);
	}

	@Test
	void getDetailBackfillsSpuNameAndImagesForAllSessions() {
		stubActivityWithTwoSessions();
		when(remoteGoodsSpuService.getSpuByIds(List.of("spu-a", "spu-b"))).thenReturn(List.of(
				new GoodsSpu().setId("spu-a").setName("挪威三文鱼").setSpuUrls(new String[]{"http://img/a.jpg"}),
				new GoodsSpu().setId("spu-b").setName("北极甜虾").setSpuUrls(new String[]{"http://img/b.jpg"})));

		SeckillActivityVO detail = service.getDetail("act-1");

		assertThat(detail).isNotNull();
		SeckillGoodsVO goodsA = detail.getSessions().get(0).getGoodsList().get(0);
		assertThat(goodsA.getSpuName()).isEqualTo("挪威三文鱼");
		assertThat(goodsA.getSpuUrls()).containsExactly("http://img/a.jpg");
		assertThat(goodsA.getRemainingStock()).isEqualTo(8);
		SeckillGoodsVO goodsB = detail.getSessions().get(1).getGoodsList().get(0);
		assertThat(goodsB.getSpuName()).isEqualTo("北极甜虾");
		assertThat(goodsB.getSpuUrls()).containsExactly("http://img/b.jpg");
		// 跨场次合并为一次商品域批量查询
		verify(remoteGoodsSpuService).getSpuByIds(List.of("spu-a", "spu-b"));
	}

	@Test
	void appSessionFillsCartUserCountFromOrderDomain() {
		stubAppSession();
		when(remoteShoppingCartService.countDistinctSkuUsersSince(anyString(), anyList(), any(LocalDateTime.class)))
			.thenReturn(23);

		AppSeckillVO vo = service.getSessionGoods("session-1");

		assertThat(vo).isNotNull();
		assertThat(vo.getCartUserCount()).isEqualTo(23);
	}

	@Test
	void appSessionDegradesWhenCartStatFails() {
		stubAppSession();
		when(remoteShoppingCartService.countDistinctSkuUsersSince(anyString(), anyList(), any(LocalDateTime.class)))
			.thenThrow(new RuntimeException("dubbo timeout"));

		AppSeckillVO vo = service.getSessionGoods("session-1");

		assertThat(vo).isNotNull();
		// 统计失败 = 不展示，不影响会场加载
		assertThat(vo.getCartUserCount()).isNull();
		assertThat(vo.getGoodsList()).hasSize(1);
	}

	private void stubAppSession() {
		ArynTenantContextHolder.setTenantId("tenant-1");
		SeckillSession session = new SeckillSession();
		session.setId("session-1");
		session.setActivityId("act-1");
		session.setSessionName("11:00 场");
		session.setStartTime(LocalDateTime.now().minusHours(1));
		session.setEndTime(LocalDateTime.now().plusHours(1));
		when(seckillSessionService.getById("session-1")).thenReturn(session);
		SeckillGoods goods = new SeckillGoods();
		goods.setSkuId("sku-1");
		goods.setActivityId("act-1");
		goods.setSessionId("session-1");
		goods.setSeckillStock(10);
		goods.setSoldCount(3);
		when(seckillGoodsService.listBySessionId("session-1")).thenReturn(List.of(goods));
		when(remoteGoodsSkuService.getBySkuIds(List.of("sku-1"))).thenReturn(List.of());
		when(seckillStockManager.batchGetRemainingStock(anyList())).thenReturn(List.of(7));
	}

	@Test
	void getDetailDegradesToSpuIdWhenProductDomainUnavailable() {
		stubActivityWithTwoSessions();
		when(remoteGoodsSpuService.getSpuByIds(anyList())).thenThrow(new RuntimeException("dubbo timeout"));

		SeckillActivityVO detail = service.getDetail("act-1");

		SeckillGoodsVO goodsA = detail.getSessions().get(0).getGoodsList().get(0);
		assertThat(goodsA.getSpuId()).isEqualTo("spu-a");
		assertThat(goodsA.getSpuName()).isNull();
		assertThat(goodsA.getSpuUrls()).isNull();
		// 详情其余字段不受商品域故障影响
		assertThat(goodsA.getRemainingStock()).isEqualTo(8);
	}

	@Test
	void getDetailSkipsProductLookupWhenNoGoods() {
		when(activityMapper.selectById("act-1")).thenReturn(new SeckillActivity().setId("act-1"));
		when(seckillSessionService.listByActivityId("act-1")).thenReturn(List.of(
				new SeckillSession().setId("s-1").setActivityId("act-1")));
		when(seckillGoodsService.listBySessionId("s-1")).thenReturn(List.of());

		SeckillActivityVO detail = service.getDetail("act-1");

		assertThat(detail.getSessions()).hasSize(1);
		verifyNoInteractions(remoteGoodsSpuService);
	}

	@Test
	void getDetailReturnsNullWhenActivityMissing() {
		when(activityMapper.selectById("act-404")).thenReturn(null);

		assertThat(service.getDetail("act-404")).isNull();
		verifyNoInteractions(remoteGoodsSpuService);
	}

	private void stubActivityWithTwoSessions() {
		when(activityMapper.selectById("act-1"))
			.thenReturn(new SeckillActivity().setId("act-1").setActivityName("晚间秒杀"));
		when(seckillSessionService.listByActivityId("act-1")).thenReturn(List.of(
				new SeckillSession().setId("s-1").setActivityId("act-1").setSessionName("场次1"),
				new SeckillSession().setId("s-2").setActivityId("act-1").setSessionName("场次2")));
		when(seckillGoodsService.listBySessionId("s-1")).thenReturn(List.of(
				new SeckillGoods().setSpuId("spu-a").setSkuId("sku-a").setSeckillStock(10).setSoldCount(2)));
		when(seckillGoodsService.listBySessionId("s-2")).thenReturn(List.of(
				new SeckillGoods().setSpuId("spu-b").setSkuId("sku-b").setSeckillStock(5).setSoldCount(0)));
	}

}
