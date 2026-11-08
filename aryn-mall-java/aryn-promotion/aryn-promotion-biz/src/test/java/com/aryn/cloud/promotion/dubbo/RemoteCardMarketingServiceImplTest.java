package com.aryn.cloud.promotion.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.dto.GoodsCardTagDTO;
import com.aryn.cloud.promotion.api.dto.GoodsSkuSeckillDTO;
import com.aryn.cloud.promotion.api.entity.SeckillGoods;
import com.aryn.cloud.promotion.api.vo.AppNewbiePriceVO;
import com.aryn.cloud.promotion.service.INewbieActivityService;
import com.aryn.cloud.promotion.service.ISeckillActivityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 商品卡营销标签远程实现的聚合与降级契约。
 *
 * <p>卡面标签是增强展示，本实现的硬约束是「任何一路失败只降级该路，另一路照常；
 * 整体异常降级空列表不穿透」。资格口径（未登录可享、登录 fail-closed）复用
 * getSkuPrices 自身语义，这里钉住消费侧的判定位置。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("商品卡营销标签远程聚合")
class RemoteCardMarketingServiceImplTest {

	@Mock
	private INewbieActivityService newbieActivityService;

	@Mock
	private ISeckillActivityService seckillActivityService;

	@Mock
	private RemoteGoodsSkuService remoteGoodsSkuService;

	private RemoteCardMarketingServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new RemoteCardMarketingServiceImpl(newbieActivityService, seckillActivityService);
		ReflectionTestUtils.setField(service, "remoteGoodsSkuService", remoteGoodsSkuService);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	@DisplayName("缺租户/空入参直接返回空且不打任何远程")
	void blankTenantOrEmptySpuIdsReturnEmptyWithoutRemoteCalls() {
		assertThat(service.batchCardTags(null, "u1", List.of("spu-1"))).isEmpty();
		assertThat(service.batchCardTags(" ", "u1", List.of("spu-1"))).isEmpty();
		assertThat(service.batchCardTags("t1", "u1", List.of())).isEmpty();
		assertThat(service.batchCardTags("t1", "u1", List.of(" ", ""))).isEmpty();
		verifyNoInteractions(remoteGoodsSkuService, newbieActivityService, seckillActivityService);
	}

	@Test
	@DisplayName("未登录（eligible=null）按潜在新人口径下发新人价")
	void anonymousUserGetsNewbiePrice() {
		when(remoteGoodsSkuService.listBySpuIds(anyList())).thenReturn(List.of(sku("sku-1", "spu-1")));
		when(newbieActivityService.getSkuPrices(anyList(), isNull()))
			.thenReturn(newbieVo(null, newbieItem("sku-1", "0.01")));
		when(seckillActivityService.getActiveSeckillGoodsBySpuIds(anyList())).thenReturn(List.of());

		List<GoodsCardTagDTO> tags = service.batchCardTags("t1", null, List.of("spu-1"));

		assertThat(tags).hasSize(1);
		assertThat(tags.get(0).getSpuId()).isEqualTo("spu-1");
		assertThat(tags.get(0).getNewbiePrice()).isEqualByComparingTo("0.01");
		assertThat(tags.get(0).getSeckillPrice()).isNull();
	}

	@Test
	@DisplayName("登录用户资格不合格时不下发新人价，秒杀标签照常")
	void rejectedNewbieKeepsSeckillTag() {
		when(remoteGoodsSkuService.listBySpuIds(anyList())).thenReturn(List.of(sku("sku-1", "spu-1")));
		when(newbieActivityService.getSkuPrices(anyList(), anyString()))
			.thenReturn(newbieVo(Boolean.FALSE, newbieItem("sku-1", "0.01")));
		when(seckillActivityService.getActiveSeckillGoodsBySpuIds(anyList()))
			.thenReturn(List.of(seckill("spu-1", "9.90")));

		List<GoodsCardTagDTO> tags = service.batchCardTags("t1", "u1", List.of("spu-1"));

		assertThat(tags).hasSize(1);
		assertThat(tags.get(0).getNewbiePrice()).isNull();
		assertThat(tags.get(0).getSeckillPrice()).isEqualByComparingTo("9.90");
	}

	@Test
	@DisplayName("新人价与秒杀同时命中合并为同一 SPU 的标签")
	void bothTagsMergedIntoSameDto() {
		when(remoteGoodsSkuService.listBySpuIds(anyList())).thenReturn(List.of(sku("sku-1", "spu-1")));
		when(newbieActivityService.getSkuPrices(anyList(), isNull()))
			.thenReturn(newbieVo(null, newbieItem("sku-1", "0.01")));
		when(seckillActivityService.getActiveSeckillGoodsBySpuIds(anyList()))
			.thenReturn(List.of(seckill("spu-1", "9.90")));

		List<GoodsCardTagDTO> tags = service.batchCardTags("t1", null, List.of("spu-1"));

		assertThat(tags).hasSize(1);
		assertThat(tags.get(0).getNewbiePrice()).isEqualByComparingTo("0.01");
		assertThat(tags.get(0).getSeckillPrice()).isEqualByComparingTo("9.90");
	}

	@Test
	@DisplayName("同 SPU 多 SKU/多场次取最低可享价")
	void sameSpuAggregatesLowestPrices() {
		when(remoteGoodsSkuService.listBySpuIds(anyList()))
			.thenReturn(List.of(sku("sku-1", "spu-1"), sku("sku-2", "spu-1")));
		when(newbieActivityService.getSkuPrices(anyList(), isNull()))
			.thenReturn(newbieVo(null, newbieItem("sku-1", "2.00"), newbieItem("sku-2", "1.50")));
		when(seckillActivityService.getActiveSeckillGoodsBySpuIds(anyList()))
			.thenReturn(List.of(seckill("spu-1", "9.90"), seckill("spu-1", "8.80")));

		List<GoodsCardTagDTO> tags = service.batchCardTags("t1", null, List.of("spu-1"));

		assertThat(tags).hasSize(1);
		assertThat(tags.get(0).getNewbiePrice()).isEqualByComparingTo("1.50");
		assertThat(tags.get(0).getSeckillPrice()).isEqualByComparingTo("8.80");
	}

	@Test
	@DisplayName("新人价查询异常只降级新人价，秒杀照常下发")
	void newbieFailureDegradesToSeckillOnly() {
		when(remoteGoodsSkuService.listBySpuIds(anyList())).thenReturn(List.of(sku("sku-1", "spu-1")));
		when(newbieActivityService.getSkuPrices(anyList(), isNull())).thenThrow(new RuntimeException("boom"));
		when(seckillActivityService.getActiveSeckillGoodsBySpuIds(anyList()))
			.thenReturn(List.of(seckill("spu-1", "9.90")));

		List<GoodsCardTagDTO> tags = service.batchCardTags("t1", null, List.of("spu-1"));

		assertThat(tags).hasSize(1);
		assertThat(tags.get(0).getNewbiePrice()).isNull();
		assertThat(tags.get(0).getSeckillPrice()).isEqualByComparingTo("9.90");
	}

	@Test
	@DisplayName("SPU→SKU 解析失败只降级新人价，秒杀不依赖该映射照常下发")
	void skuResolutionFailureDegradesToSeckillOnly() {
		when(remoteGoodsSkuService.listBySpuIds(anyList())).thenThrow(new RuntimeException("boom"));
		when(seckillActivityService.getActiveSeckillGoodsBySpuIds(anyList()))
			.thenReturn(List.of(seckill("spu-1", "9.90")));

		List<GoodsCardTagDTO> tags = service.batchCardTags("t1", null, List.of("spu-1"));

		assertThat(tags).hasSize(1);
		assertThat(tags.get(0).getSeckillPrice()).isEqualByComparingTo("9.90");
		verify(newbieActivityService, never()).getSkuPrices(anyList(), anyString());
		verify(newbieActivityService, never()).getSkuPrices(anyList(), isNull());
	}

	@Test
	@DisplayName("SPU 入参截断到单次上限")
	void spuIdsClampedToMax() {
		List<String> tooMany = IntStream.rangeClosed(1, 205).mapToObj(i -> "spu-" + i).toList();
		when(remoteGoodsSkuService.listBySpuIds(anyList())).thenReturn(List.of());
		when(seckillActivityService.getActiveSeckillGoodsBySpuIds(anyList())).thenReturn(List.of());

		service.batchCardTags("t1", null, tooMany);

		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(remoteGoodsSkuService).listBySpuIds(captor.capture());
		assertThat(captor.getValue()).hasSize(200);
	}

	@Test
	@DisplayName("调用完成后租户上下文还原（入参前无上下文则清空）")
	void tenantContextRestoredAfterCall() {
		when(remoteGoodsSkuService.listBySpuIds(anyList())).thenReturn(List.of(sku("sku-1", "spu-1")));
		when(newbieActivityService.getSkuPrices(anyList(), isNull())).thenReturn(newbieVo(null));
		when(seckillActivityService.getActiveSeckillGoodsBySpuIds(anyList())).thenReturn(List.of());

		service.batchCardTags("t1", null, List.of("spu-1"));

		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	@DisplayName("batchActiveSeckill：缺租户/空入参直接返回空且不打内部服务")
	void batchSeckillBlankInputReturnsEmpty() {
		assertThat(service.batchActiveSeckill(null, List.of("sku-1"))).isEmpty();
		assertThat(service.batchActiveSeckill(" ", List.of("sku-1"))).isEmpty();
		assertThat(service.batchActiveSeckill("t1", List.of())).isEmpty();
		assertThat(service.batchActiveSeckill("t1", List.of(" ", ""))).isEmpty();
		verifyNoInteractions(seckillActivityService);
	}

	@Test
	@DisplayName("batchActiveSeckill：SKU 维度保留，同 SKU 多场次取最低")
	void batchSeckillKeepsSkuDimensionAndAggregatesLowest() {
		when(seckillActivityService.getActiveSeckillGoodsBySkuIds(anyList()))
			.thenReturn(List.of(
				seckillSku("sku-1", "9.90"), seckillSku("sku-1", "8.80"),
				seckillSku("sku-2", "5.00")));

		List<GoodsSkuSeckillDTO> prices = service.batchActiveSeckill("t1", List.of("sku-1", "sku-2"));

		assertThat(prices).hasSize(2);
		assertThat(prices.stream().filter(dto -> "sku-1".equals(dto.getSkuId())).findFirst().orElseThrow()
			.getSeckillPrice()).isEqualByComparingTo("8.80");
		assertThat(prices.stream().filter(dto -> "sku-2".equals(dto.getSkuId())).findFirst().orElseThrow()
			.getSeckillPrice()).isEqualByComparingTo("5.00");
	}

	@Test
	@DisplayName("batchActiveSeckill：脏行（无 skuId/非正价）剔除、异常降级空且上下文还原")
	void batchSeckillFiltersDirtyRowsAndDegrades() {
		SeckillGoods noSku = seckillSku(null, "1.00");
		SeckillGoods badPrice = seckillSku("sku-2", "0.00");
		when(seckillActivityService.getActiveSeckillGoodsBySkuIds(anyList()))
			.thenReturn(new ArrayList<>(List.of(noSku, badPrice, seckillSku("sku-3", "3.00"))));

		List<GoodsSkuSeckillDTO> prices = service.batchActiveSeckill("t1", List.of("sku-2", "sku-3"));

		assertThat(prices).hasSize(1);
		assertThat(prices.get(0).getSkuId()).isEqualTo("sku-3");

		when(seckillActivityService.getActiveSeckillGoodsBySkuIds(anyList()))
			.thenThrow(new RuntimeException("boom"));
		assertThat(service.batchActiveSeckill("t1", List.of("sku-1"))).isEmpty();
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	private GoodsSku sku(String id, String spuId) {
		GoodsSku sku = new GoodsSku();
		sku.setId(id);
		sku.setSpuId(spuId);
		return sku;
	}

	private AppNewbiePriceVO.NewbieSkuPriceVO newbieItem(String skuId, String price) {
		AppNewbiePriceVO.NewbieSkuPriceVO item = new AppNewbiePriceVO.NewbieSkuPriceVO();
		item.setSkuId(skuId);
		item.setSpuId("spu-of-" + skuId);
		item.setNewbiePrice(new BigDecimal(price));
		return item;
	}

	private AppNewbiePriceVO newbieVo(Boolean eligible, AppNewbiePriceVO.NewbieSkuPriceVO... items) {
		AppNewbiePriceVO vo = new AppNewbiePriceVO();
		vo.setEligible(eligible);
		vo.setItems(new ArrayList<>(List.of(items)));
		return vo;
	}

	private SeckillGoods seckill(String spuId, String price) {
		return new SeckillGoods().setSpuId(spuId).setSeckillPrice(new BigDecimal(price));
	}

	private SeckillGoods seckillSku(String skuId, String price) {
		return new SeckillGoods().setSkuId(skuId).setSeckillPrice(price == null ? null : new BigDecimal(price));
	}

}
