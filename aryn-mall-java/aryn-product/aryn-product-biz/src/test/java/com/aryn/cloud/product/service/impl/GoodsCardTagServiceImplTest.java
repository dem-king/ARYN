package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.order.api.dto.GoodsSpuSalesStatDTO;
import com.aryn.cloud.order.api.remote.RemoteGoodsSalesStatService;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.GoodsCardTagVO;
import com.aryn.cloud.product.mapper.GoodsSkuMapper;
import com.aryn.cloud.product.mapper.GoodsSkuPriceDailyMapper;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import com.aryn.cloud.product.support.SkuPriceWindowStat;
import com.aryn.cloud.promotion.api.dto.GoodsCardTagDTO;
import com.aryn.cloud.promotion.api.remote.RemoteCardMarketingService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 商品卡营销标签聚合服务的装配与降级契约。
 *
 * <p>本服务是 card-tags 端点的唯一装配点：入参清洗/截断、promotion（新人+秒杀）与
 * order（社证）两路合并、每路独立降级（一路失败不牵连另一路）、远程异常降级空列表
 * ——商品卡永远不因营销标签挂掉而缺商品。社证门槛/取整口径在 {@code SocialProofFormatterTest} 钉。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("商品卡营销标签聚合服务")
class GoodsCardTagServiceImplTest {

	@Mock
	private GoodsSpuMapper goodsSpuMapper;

	@Mock
	private GoodsSkuMapper goodsSkuMapper;

	@Mock
	private GoodsSkuPriceDailyMapper goodsSkuPriceDailyMapper;

	@Mock
	private RemoteCardMarketingService remoteCardMarketingService;

	@Mock
	private RemoteGoodsSalesStatService remoteGoodsSalesStatService;

	private GoodsCardTagServiceImpl service;

	@BeforeEach
	void setUp() {
		// fillPriceTag 的 wrapper 用 lambda 取列，需要实体 TableInfo 缓存（ZoneTagBatchRecordServiceImplTest 同款）
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GoodsSpu.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GoodsSku.class);
		service = new GoodsCardTagServiceImpl(goodsSpuMapper, goodsSkuMapper, goodsSkuPriceDailyMapper);
		ReflectionTestUtils.setField(service, "remoteCardMarketingService", remoteCardMarketingService);
		ReflectionTestUtils.setField(service, "remoteGoodsSalesStatService", remoteGoodsSalesStatService);
		ReflectionTestUtils.setField(service, "minBuyerCnt", 1000);
		ReflectionTestUtils.setField(service, "minRepurchaseRate", 30);
		ReflectionTestUtils.setField(service, "priceTagWindowDays", 14);
		ReflectionTestUtils.setField(service, "priceTagEnabled", true);
	}

	@Test
	@DisplayName("空入参直接返回空且不打远程")
	void emptyInputReturnsEmptyWithoutRemoteCall() {
		assertThat(service.cardTags(null)).isEmpty();
		assertThat(service.cardTags(List.of())).isEmpty();
		assertThat(service.cardTags(List.of(" ", ""))).isEmpty();
		verifyNoInteractions(remoteCardMarketingService, remoteGoodsSalesStatService);
	}

	@Test
	@DisplayName("入参去重并截断到单次上限")
	void inputDeduplicatedAndClamped() {
		when(remoteCardMarketingService.batchCardTags(any(), any(), anyList())).thenReturn(List.of());
		when(remoteGoodsSalesStatService.latestStats(any(), anyList())).thenReturn(List.of());

		// 205 个去重值 + 3 个重复 = 208 入参 → 去重 205 → 截断 200
		List<String> input = new ArrayList<>(IntStream.rangeClosed(1, 205).mapToObj(i -> "spu-" + i).toList());
		input.addAll(List.of("spu-1", "spu-2", "spu-3"));

		service.cardTags(input);

		ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
		verify(remoteCardMarketingService).batchCardTags(any(), any(), captor.capture());
		assertThat(captor.getValue()).hasSize(200);
		assertThat(captor.getValue()).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("promotion 异常只降级价签，社证照常下发")
	void promotionFailureDegradesToSocialProofOnly() {
		when(remoteCardMarketingService.batchCardTags(any(), any(), anyList()))
			.thenThrow(new RuntimeException("boom"));
		when(remoteGoodsSalesStatService.latestStats(any(), anyList()))
			.thenReturn(List.of(stat("spu-1", 4000, 1600, new BigDecimal("40.00"))));

		List<GoodsCardTagVO> tags = service.cardTags(List.of("spu-1"));

		assertThat(tags).hasSize(1);
		assertThat(tags.get(0).getNewbiePrice()).isNull();
		assertThat(tags.get(0).getSeckillPrice()).isNull();
		assertThat(tags.get(0).getSocialProofText()).isEqualTo("40% 的人回购");
		assertThat(tags.get(0).getSocialProofKind()).isEqualTo("repurchase");
	}

	@Test
	@DisplayName("社证异常只降级文案，新人/秒杀价签照常下发")
	void socialProofFailureDegradesToPromotionOnly() {
		GoodsCardTagDTO dto = new GoodsCardTagDTO();
		dto.setSpuId("spu-1");
		dto.setSeckillPrice(new BigDecimal("9.90"));
		when(remoteCardMarketingService.batchCardTags(any(), any(), anyList())).thenReturn(List.of(dto));
		when(remoteGoodsSalesStatService.latestStats(any(), anyList())).thenThrow(new RuntimeException("boom"));

		List<GoodsCardTagVO> tags = service.cardTags(List.of("spu-1"));

		assertThat(tags).hasSize(1);
		assertThat(tags.get(0).getSeckillPrice()).isEqualByComparingTo("9.90");
		assertThat(tags.get(0).getSocialProofText()).isNull();
	}

	@Test
	@DisplayName("同一 SPU 价签与社证合并为同一标签行")
	void promotionAndSocialProofMergedIntoSameRow() {
		GoodsCardTagDTO dto = new GoodsCardTagDTO();
		dto.setSpuId("spu-1");
		dto.setNewbiePrice(new BigDecimal("0.01"));
		when(remoteCardMarketingService.batchCardTags(any(), any(), anyList())).thenReturn(List.of(dto));
		when(remoteGoodsSalesStatService.latestStats(any(), anyList()))
			.thenReturn(List.of(stat("spu-1", 3000, 900, new BigDecimal("29.50"))));

		List<GoodsCardTagVO> tags = service.cardTags(List.of("spu-1"));

		assertThat(tags).hasSize(1);
		assertThat(tags.get(0).getNewbiePrice()).isEqualByComparingTo("0.01");
		// 回购率 29.5<30 且回头客 900<1000：回落下单人数（优先级链在 FormatterTest 钉死）
		assertThat(tags.get(0).getSocialProofText()).isEqualTo("月 3000+ 人已下单");
		assertThat(tags.get(0).getSocialProofKind()).isEqualTo("buyer");
	}

	@Test
	@DisplayName("只命中社证的 SPU 也产出标签行（无价签）")
	void socialProofOnlySpuProducesRow() {
		when(remoteCardMarketingService.batchCardTags(any(), any(), anyList())).thenReturn(List.of());
		when(remoteGoodsSalesStatService.latestStats(any(), anyList()))
			.thenReturn(List.of(stat("spu-2", 12000, 3000, new BigDecimal("20.00"))));

		List<GoodsCardTagVO> tags = service.cardTags(List.of("spu-2"));

		assertThat(tags).hasSize(1);
		// 回购率 20<30：回落回头客（3000 达门槛）
		assertThat(tags.get(0).getSocialProofText()).isEqualTo("月 3000+ 位回头客");
		assertThat(tags.get(0).getSocialProofKind()).isEqualTo("repeat");
	}

	@Test
	@DisplayName("低于门槛的社证不下发文案（行也不产出）")
	void belowThresholdSocialProofSuppressed() {
		when(remoteCardMarketingService.batchCardTags(any(), any(), anyList())).thenReturn(List.of());
		when(remoteGoodsSalesStatService.latestStats(any(), anyList()))
			.thenReturn(List.of(stat("spu-3", 999, 30, new BigDecimal("80.00"))));

		assertThat(service.cardTags(List.of("spu-3"))).isEmpty();
	}

	@Test
	@DisplayName("远程返回的脏行（无 spuId）被剔除")
	void dirtyRowsFiltered() {
		GoodsCardTagDTO dirty = new GoodsCardTagDTO();
		dirty.setNewbiePrice(BigDecimal.ONE);
		GoodsCardTagDTO clean = new GoodsCardTagDTO();
		clean.setSpuId("spu-2");
		clean.setSeckillPrice(BigDecimal.TEN);
		when(remoteCardMarketingService.batchCardTags(any(), any(), anyList())).thenReturn(List.of(dirty, clean));
		when(remoteGoodsSalesStatService.latestStats(any(), anyList())).thenReturn(List.of());

		List<GoodsCardTagVO> tags = service.cardTags(List.of("spu-1", "spu-2"));

		assertThat(tags).hasSize(1);
		assertThat(tags.get(0).getSpuId()).isEqualTo("spu-2");
	}

	// ==================== P3 近N天最低价 ====================

	@Test
	@DisplayName("三条件全满足：当日价=窗口min、变过价、低于原价 → 出标")
	void priceTagIssuedWhenAllConditionsMet() {
		mockSkuGraph("spu-1", "29.90", "sku-1");
		mockWindowStat("sku-1", "19.90", 2L, "19.90");

		List<GoodsCardTagVO> tags = service.cardTags(List.of("spu-1"));

		assertThat(tags).hasSize(1);
		assertThat(tags.get(0).getPriceTagText()).isEqualTo("近14天最低价");
	}

	@Test
	@DisplayName("窗口内常平价（kinds=1）不出标")
	void flatPriceSuppressed() {
		mockSkuGraph("spu-1", "29.90", "sku-1");
		mockWindowStat("sku-1", "19.90", 1L, "19.90");

		assertThat(service.cardTags(List.of("spu-1"))).isEmpty();
	}

	@Test
	@DisplayName("当日价高于窗口最低价（涨回去了）不出标")
	void priceReboundedSuppressed() {
		mockSkuGraph("spu-1", "29.90", "sku-1");
		mockWindowStat("sku-1", "15.00", 2L, "19.90");

		assertThat(service.cardTags(List.of("spu-1"))).isEmpty();
	}

	@Test
	@DisplayName("当日价不低于原价（无划线空间）不出标；原价缺失同样不出标")
	void noStrikeRoomSuppressed() {
		// 当日价 == 原价
		mockSkuGraph("spu-1", "19.90", "sku-1");
		mockWindowStat("sku-1", "19.90", 2L, "19.90");
		assertThat(service.cardTags(List.of("spu-1"))).isEmpty();

		// 原价缺失
		mockSkuGraph("spu-1", null, "sku-1");
		assertThat(service.cardTags(List.of("spu-1"))).isEmpty();
	}

	@Test
	@DisplayName("当日无快照行（Job 未跑/当天不在售）不出标")
	void noTodaySnapshotSuppressed() {
		mockSkuGraph("spu-1", "29.90", "sku-1");
		// 有历史 min 但当日价缺失
		mockWindowStat("sku-1", "15.00", 2L, null);

		assertThat(service.cardTags(List.of("spu-1"))).isEmpty();
	}

	@Test
	@DisplayName("主价驱动 SKU 判定：达标 SKU 非主价（另一 SKU 更低）不出标")
	void nonDrivingQualifierSuppressed() {
		// sku-1 今日 8.00（更低价=主价，常平 kinds=1）；sku-2 今日 9.90 达标——但主价是 sku-1；
		// 原价 12 高于所有现价：本用例只隔离「驱动 SKU 是否达标」
		mockSkuGraph("spu-1", "12.00", "sku-1", "sku-2");
		mockWindowStats(
			windowStat("sku-1", "8.00", 1L, "8.00"),
			windowStat("sku-2", "9.90", 2L, "9.90"));

		assertThat(service.cardTags(List.of("spu-1"))).isEmpty();
	}

	@Test
	@DisplayName("同价多 SKU：任一达标即出标")
	void tiedDrivingSkuQualifies() {
		mockSkuGraph("spu-1", "19.90", "sku-1", "sku-2");
		mockWindowStats(
			windowStat("sku-1", "9.90", 1L, "9.90"),
			windowStat("sku-2", "9.90", 2L, "9.90"));

		List<GoodsCardTagVO> tags = service.cardTags(List.of("spu-1"));

		assertThat(tags).hasSize(1);
		assertThat(tags.get(0).getPriceTagText()).isEqualTo("近14天最低价");
	}

	@Test
	@DisplayName("窗口最低价来自当天不在售 SKU 的历史行时，展示价高于它就不出标（声明对全窗口成立）")
	void offSaleSkuHistoryBlocksClaim() {
		// sku-1 今日 9.90 达标；sku-2 已不在售（无当日价）但历史最低 8.00
		mockSkuGraph("spu-1", "19.90", "sku-1", "sku-2");
		mockWindowStats(
			windowStat("sku-1", "9.90", 2L, "9.90"),
			windowStat("sku-2", "8.00", 2L, null));

		assertThat(service.cardTags(List.of("spu-1"))).isEmpty();
	}

	@Test
	@DisplayName("窗口天数配置驱动文案；开关关闭或窗口非法不出标")
	void windowDaysDrivesTextAndSwitchHonored() {
		// 窗口 7 天 → 文案「近7天最低价」
		mockSkuGraph("spu-1", "29.90", "sku-1");
		mockWindowStat("sku-1", "19.90", 2L, "19.90");
		ReflectionTestUtils.setField(service, "priceTagWindowDays", 7);
		assertThat(service.cardTags(List.of("spu-1")).get(0).getPriceTagText()).isEqualTo("近7天最低价");

		// 开关关闭
		ReflectionTestUtils.setField(service, "priceTagEnabled", false);
		assertThat(service.cardTags(List.of("spu-1"))).isEmpty();
	}

	@Test
	@DisplayName("快照判定异常只降级价格声明，价签/社证照常下发")
	void priceTagFailureDegradesIndependently() {
		GoodsCardTagDTO dto = new GoodsCardTagDTO();
		dto.setSpuId("spu-1");
		dto.setSeckillPrice(new BigDecimal("9.90"));
		when(remoteCardMarketingService.batchCardTags(any(), any(), anyList())).thenReturn(List.of(dto));
		when(remoteGoodsSalesStatService.latestStats(any(), anyList())).thenReturn(List.of());
		when(goodsSpuMapper.selectList(any(Wrapper.class))).thenThrow(new RuntimeException("boom"));

		List<GoodsCardTagVO> tags = service.cardTags(List.of("spu-1"));

		assertThat(tags).hasSize(1);
		assertThat(tags.get(0).getSeckillPrice()).isEqualByComparingTo("9.90");
		assertThat(tags.get(0).getPriceTagText()).isNull();
	}

	@Test
	@DisplayName("商品/SKU 全部下架（本域查空）不出标且不影响其它路")
	void missingSpuOrSkuSuppressed() {
		GoodsCardTagDTO dto = new GoodsCardTagDTO();
		dto.setSpuId("spu-1");
		dto.setNewbiePrice(new BigDecimal("0.01"));
		when(remoteCardMarketingService.batchCardTags(any(), any(), anyList())).thenReturn(List.of(dto));
		when(remoteGoodsSalesStatService.latestStats(any(), anyList())).thenReturn(List.of());
		when(goodsSpuMapper.selectList(any(Wrapper.class))).thenReturn(List.of());

		List<GoodsCardTagVO> tags = service.cardTags(List.of("spu-1"));

		assertThat(tags).hasSize(1);
		assertThat(tags.get(0).getNewbiePrice()).isEqualByComparingTo("0.01");
		assertThat(tags.get(0).getPriceTagText()).isNull();
	}

	// ==================== fixtures ====================

	private void mockSkuGraph(String spuId, String originalPrice, String... skuIds) {
		GoodsSpu spu = new GoodsSpu();
		spu.setId(spuId);
		if (originalPrice != null) {
			spu.setOriginalPrice(new BigDecimal(originalPrice));
		}
		when(goodsSpuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(spu));
		List<GoodsSku> skus = new ArrayList<>();
		for (String skuId : skuIds) {
			GoodsSku sku = new GoodsSku();
			sku.setId(skuId);
			sku.setSpuId(spuId);
			skus.add(sku);
		}
		when(goodsSkuMapper.selectList(any(Wrapper.class))).thenReturn(skus);
	}

	private SkuPriceWindowStat windowStat(String skuId, String minPrice, Long priceKinds, String todayPrice) {
		SkuPriceWindowStat stat = new SkuPriceWindowStat();
		stat.setSkuId(skuId);
		if (minPrice != null) {
			stat.setMinPrice(new BigDecimal(minPrice));
		}
		stat.setPriceKinds(priceKinds);
		if (todayPrice != null) {
			stat.setTodayPrice(new BigDecimal(todayPrice));
		}
		return stat;
	}

	private void mockWindowStat(String skuId, String minPrice, Long priceKinds, String todayPrice) {
		mockWindowStats(windowStat(skuId, minPrice, priceKinds, todayPrice));
	}

	private void mockWindowStats(SkuPriceWindowStat... stats) {
		when(goodsSkuPriceDailyMapper.selectWindowStats(any(), anyList(), any(LocalDate.class), any(LocalDate.class)))
			.thenReturn(List.of(stats));
	}


	private GoodsSpuSalesStatDTO stat(String spuId, int buyerCnt, int repeatBuyerCnt, BigDecimal rate) {
		GoodsSpuSalesStatDTO stat = new GoodsSpuSalesStatDTO();
		stat.setSpuId(spuId);
		stat.setBuyerCnt(buyerCnt);
		stat.setRepeatBuyerCnt(repeatBuyerCnt);
		stat.setRepurchaseRate(rate);
		return stat;
	}

}
