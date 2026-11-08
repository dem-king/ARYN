package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSkuPriceDaily;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.AppClearancePriceVO;
import com.aryn.cloud.product.mapper.GoodsSkuMapper;
import com.aryn.cloud.product.mapper.GoodsSkuPriceDailyMapper;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import com.aryn.cloud.product.service.IGoodsZoneTagService;
import com.aryn.cloud.promotion.api.dto.GoodsSkuSeckillDTO;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * SKU 价格快照服务的口径与降级契约。
 *
 * <p>快照价 = MIN(标价, 出清生效价, 秒杀价)（非个性化到手价，口径即合规证据）：
 * 出清价走专区同源口径、秒杀价走远程批量，两路各自分批独立降级（一路失败该批
 * 仅本地价），标价取 SKU 售价缺省回落 SPU 售价；upsert 分片防大租户单语句过大。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SKU价格快照服务")
class GoodsSkuPriceDailyServiceImplTest {

	@Mock
	private GoodsSpuMapper goodsSpuMapper;

	@Mock
	private GoodsSkuMapper goodsSkuMapper;

	@Mock
	private GoodsSkuPriceDailyMapper goodsSkuPriceDailyMapper;

	@Mock
	private IGoodsZoneTagService goodsZoneTagService;

	@Mock
	private RemoteCardMarketingService remoteCardMarketingService;

	private GoodsSkuPriceDailyServiceImpl service;

	@BeforeEach
	void setUp() {
		// 服务内 wrapper 用 lambda 取列，需要实体 TableInfo 缓存（ZoneTagBatchRecordServiceImplTest 同款）
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GoodsSpu.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GoodsSku.class);
		service = new GoodsSkuPriceDailyServiceImpl(goodsSpuMapper, goodsSkuMapper, goodsSkuPriceDailyMapper,
				goodsZoneTagService);
		ReflectionTestUtils.setField(service, "remoteCardMarketingService", remoteCardMarketingService);
		// upsert 返回写入行数（snapshotDaily 的返回值即 mapper 汇总）；lenient：guards 等用例不走写入
		lenient().when(goodsSkuPriceDailyMapper.upsertDailyPrices(anyList()))
			.thenAnswer(invocation -> invocation.<List<?>>getArgument(0).size());
	}

	@Test
	@DisplayName("入参不合法直接 0 行且不打库")
	void guards() {
		assertThat(service.snapshotDaily(null, LocalDate.now())).isZero();
		assertThat(service.snapshotDaily(" ", LocalDate.now())).isZero();
		assertThat(service.snapshotDaily("t1", null)).isZero();
		verifyNoInteractions(goodsSpuMapper, goodsSkuMapper, goodsSkuPriceDailyMapper);
	}

	@Test
	@DisplayName("无在售商品/SKU 直接 0 行，不查出清/秒杀")
	void emptyCatalogShortCircuits() {
		when(goodsSpuMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
		assertThat(service.snapshotDaily("t1", LocalDate.now())).isZero();

		when(goodsSpuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(spu("spu-1")));
		when(goodsSkuMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
		assertThat(service.snapshotDaily("t1", LocalDate.now())).isZero();

		verify(goodsZoneTagService, never()).clearancePrices(anyList());
		verify(remoteCardMarketingService, never()).batchActiveSeckill(anyString(), anyList());
	}

	@Test
	@DisplayName("快照价=MIN(标价,出清,秒杀)：秒杀 70 胜出；无活动行用标价")
	void snapshotTakesMinOfCandidates() {
		mockCatalog(sku("sku-1", "spu-1", "100.00"), sku("sku-2", "spu-1", null));
		mockClearance(clearanceItem("sku-1", "80.00"));
		mockSeckill(seckillDto("sku-1", "70.00"));

		int rows = service.snapshotDaily("t1", LocalDate.of(2026, 11, 8));

		assertThat(rows).isEqualTo(2);
		ArgumentCaptor<List<GoodsSkuPriceDaily>> captor = ArgumentCaptor.forClass(List.class);
		verify(goodsSkuPriceDailyMapper).upsertDailyPrices(captor.capture());
		List<GoodsSkuPriceDaily> written = captor.getValue();
		// sku-1：MIN(100, 80, 70)=70；sku-2 标价回落 SPU 90，无活动价 → 90
		assertThat(written).extracting(GoodsSkuPriceDaily::getSalePrice)
			.usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
			.containsExactly(new BigDecimal("70.00"), new BigDecimal("90.00"));
		assertThat(written).allSatisfy(row -> {
			assertThat(row.getTenantId()).isEqualTo("t1");
			assertThat(row.getStatDate()).isEqualTo(LocalDate.of(2026, 11, 8));
			assertThat(row.getSpuId()).isEqualTo("spu-1");
		});
	}

	@Test
	@DisplayName("出清/秒杀单批异常降级：该批仅本地价，不阻断快照")
	void activityPriceFailureDegradesToLocalPriceOnly() {
		mockCatalog(sku("sku-1", "spu-1", "100.00"));
		when(goodsZoneTagService.clearancePrices(anyList())).thenThrow(new RuntimeException("boom"));
		when(remoteCardMarketingService.batchActiveSeckill(anyString(), anyList()))
			.thenThrow(new RuntimeException("boom"));

		assertThat(service.snapshotDaily("t1", LocalDate.now())).isEqualTo(1);

		ArgumentCaptor<List<GoodsSkuPriceDaily>> captor = ArgumentCaptor.forClass(List.class);
		verify(goodsSkuPriceDailyMapper).upsertDailyPrices(captor.capture());
		assertThat(captor.getValue().get(0).getSalePrice()).isEqualByComparingTo("100.00");
	}

	@Test
	@DisplayName("倒挂/非正活动价不参与取低（快照不低于标价）")
	void invalidActivityPricesIgnored() {
		mockCatalog(sku("sku-1", "spu-1", "100.00"));
		// 出清价高于标价（clearancePrices 同源口径本就过滤，这里兜底再防一次）与 0 价秒杀
		mockClearance(clearanceItem("sku-1", "120.00"));
		mockSeckill(seckillDto("sku-1", "0.00"), seckillDto("sku-2", null));

		assertThat(service.snapshotDaily("t1", LocalDate.now())).isEqualTo(1);

		ArgumentCaptor<List<GoodsSkuPriceDaily>> captor = ArgumentCaptor.forClass(List.class);
		verify(goodsSkuPriceDailyMapper).upsertDailyPrices(captor.capture());
		assertThat(captor.getValue().get(0).getSalePrice()).isEqualByComparingTo("100.00");
	}

	@Test
	@DisplayName("大租户分片 upsert：501 SKU 触发两次写入")
	void largeTenantChunkedUpsert() {
		List<GoodsSku> skus = new ArrayList<>();
		for (int i = 1; i <= 501; i++) {
			skus.add(sku("sku-" + i, "spu-1", "10.00"));
		}
		when(goodsSpuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(spu("spu-1")));
		when(goodsSkuMapper.selectList(any(Wrapper.class))).thenReturn(skus);

		assertThat(service.snapshotDaily("t1", LocalDate.now())).isEqualTo(501);

		ArgumentCaptor<List<GoodsSkuPriceDaily>> captor = ArgumentCaptor.forClass(List.class);
		verify(goodsSkuPriceDailyMapper, times(2)).upsertDailyPrices(captor.capture());
		assertThat(captor.getAllValues().get(0)).hasSize(500);
		assertThat(captor.getAllValues().get(1)).hasSize(1);
	}

	// ==================== fixtures ====================

	private GoodsSpu spu(String id) {
		GoodsSpu spu = new GoodsSpu();
		spu.setId(id);
		spu.setSalesPrice(new BigDecimal("90.00"));
		return spu;
	}

	private GoodsSku sku(String id, String spuId, String salesPrice) {
		GoodsSku sku = new GoodsSku();
		sku.setId(id);
		sku.setSpuId(spuId);
		if (salesPrice != null) {
			sku.setSalesPrice(new BigDecimal(salesPrice));
		}
		return sku;
	}

	private void mockCatalog(GoodsSku... skus) {
		when(goodsSpuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(spu("spu-1")));
		when(goodsSkuMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>(List.of(skus)));
	}

	private AppClearancePriceVO.Item clearanceItem(String skuId, String price) {
		AppClearancePriceVO.Item item = new AppClearancePriceVO.Item();
		item.setSkuId(skuId);
		item.setClearancePrice(price == null ? null : new BigDecimal(price));
		return item;
	}

	private void mockClearance(AppClearancePriceVO.Item... items) {
		AppClearancePriceVO vo = new AppClearancePriceVO();
		vo.setItems(new ArrayList<>(List.of(items)));
		when(goodsZoneTagService.clearancePrices(anyList())).thenReturn(vo);
	}

	private GoodsSkuSeckillDTO seckillDto(String skuId, String price) {
		GoodsSkuSeckillDTO dto = new GoodsSkuSeckillDTO();
		dto.setSkuId(skuId);
		dto.setSeckillPrice(price == null ? null : new BigDecimal(price));
		return dto;
	}

	private void mockSeckill(GoodsSkuSeckillDTO... dtos) {
		when(remoteCardMarketingService.batchActiveSeckill(eq("t1"), anyList()))
			.thenReturn(new ArrayList<>(List.of(dtos)));
	}

	@Test
	@DisplayName("无 spu 归属的脏 SKU 行跳过")
	void orphanSkuSkipped() {
		when(goodsSpuMapper.selectList(any(Wrapper.class))).thenReturn(List.of(spu("spu-1")));
		GoodsSku orphan = sku("sku-x", "spu-gone", "10.00");
		when(goodsSkuMapper.selectList(any(Wrapper.class))).thenReturn(new ArrayList<>(List.of(orphan)));
		mockClearance(clearanceItem("sku-x", "5.00"));

		assertThat(service.snapshotDaily("t1", LocalDate.now())).isZero();
		verify(goodsSkuPriceDailyMapper, never()).upsertDailyPrices(anyList());
	}

}
