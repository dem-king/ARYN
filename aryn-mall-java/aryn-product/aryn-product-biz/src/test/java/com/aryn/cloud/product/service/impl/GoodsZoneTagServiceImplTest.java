package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.api.dto.GoodsZoneTagBatchDTO;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.AppClearancePriceVO;
import com.aryn.cloud.product.api.vo.GoodsZoneTagResultVO;
import com.aryn.cloud.product.mapper.GoodsSkuMapper;
import com.aryn.cloud.product.mapper.GoodsSpuMapper;
import com.aryn.cloud.promotion.api.entity.MallZoneSetting;
import com.aryn.cloud.promotion.api.remote.RemoteMallZonePriceService;
import com.aryn.cloud.promotion.api.remote.RemoteMallZoneSettingService;
import com.aryn.cloud.promotion.api.vo.MallZoneSettingSnapshotVO;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 专区标签服务契约：批量打标不误清 SKU 精调、批量查价只返回有效出清价。
 */
class GoodsZoneTagServiceImplTest {

	private static final String TENANT = "tenant-1";

	private GoodsSpuMapper goodsSpuMapper;

	private GoodsSkuMapper goodsSkuMapper;

	private GoodsPriceHistoryService goodsPriceHistoryService;

	private GoodsZoneTagServiceImpl service;

	@BeforeAll
	static void initLambdaCache() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GoodsSpu.class);
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), GoodsSku.class);
	}

	@BeforeEach
	void setUp() {
		goodsSpuMapper = mock(GoodsSpuMapper.class);
		goodsSkuMapper = mock(GoodsSkuMapper.class);
		goodsPriceHistoryService = mock(GoodsPriceHistoryService.class);
		service = new GoodsZoneTagServiceImpl(goodsSpuMapper, goodsSkuMapper, goodsPriceHistoryService,
			new com.aryn.cloud.product.service.support.GoodsZoneTagStateResolver());
		ArynTenantContextHolder.setTenantId(TENANT);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	@DisplayName("批量打标默认保留 SKU 精调，勾选覆盖后才清空")
	void batchSaveKeepsSkuOverridesUnlessOverwrite() {
		GoodsSpu spu = new GoodsSpu();
		spu.setId("spu-1");
		spu.setName("测试商品");
		spu.setIsMemberItem(0);
		when(goodsSpuMapper.selectById("spu-1")).thenReturn(spu);

		GoodsZoneTagBatchDTO dto = batchDTO(false);
		List<GoodsZoneTagResultVO> result = service.batchSave(dto);
		assertThat(result).hasSize(1);
		assertThat(result.get(0).isSuccess()).isTrue();
		verify(goodsSkuMapper, never()).update(any(), any());

		GoodsZoneTagBatchDTO overwrite = batchDTO(true);
		service.batchSave(overwrite);
		verify(goodsSkuMapper).update(any(), any());
	}

	@Test
	@DisplayName("出清专区列表：按 SKU 覆盖算有效价、过滤倒挂并回写卡片价")
	void clearancePageUsesSkuOverridesAndDropsInvalidItems() {
		GoodsSpu valid = new GoodsSpu();
		valid.setId("spu-1");
		valid.setStatus("1");
		valid.setIsClearanceItem(1);
		valid.setSalesPrice(new BigDecimal("30.00"));
		valid.setOriginalPrice(new BigDecimal("100.00"));
		GoodsSpu invalid = new GoodsSpu();
		invalid.setId("spu-2");
		invalid.setStatus("1");
		invalid.setIsClearanceItem(1);
		invalid.setSalesPrice(new BigDecimal("30.00"));
		invalid.setOriginalPrice(new BigDecimal("100.00"));
		invalid.setClearancePrice(new BigDecimal("40.00"));
		when(goodsSpuMapper.selectList(any())).thenReturn(List.of(valid, invalid));
		GoodsSku sku = sku("sku-1", "spu-1", "30.00", "18.00");
		when(goodsSkuMapper.selectList(any())).thenReturn(List.of(sku));

		IPage<GoodsSpu> result = service.clearancePage(new Page<>(1, 10), new GoodsSpu(), null);

		assertThat(result.getTotal()).isEqualTo(1);
		assertThat(result.getRecords()).hasSize(1);
		assertThat(result.getRecords().get(0).getId()).isEqualTo("spu-1");
		assertThat(result.getRecords().get(0).getClearancePrice()).isEqualByComparingTo("18.00");
	}

	@Test
	@DisplayName("批量查价：只返回未到期且低于当前售价的 SKU")
	void clearancePricesOnlyReturnsEffectiveRows() {
		GoodsSpu spu = new GoodsSpu();
		spu.setId("spu-1");
		spu.setIsClearanceItem(1);
		spu.setSalesPrice(new BigDecimal("30.00"));
		spu.setOriginalPrice(new BigDecimal("100.00"));
		spu.setClearanceExpireTime(LocalDateTime.now().plusDays(1));

		GoodsSku valid = sku("sku-1", "spu-1", "30.00", "18.00");
		GoodsSku expired = sku("sku-2", "spu-1", "30.00", "18.00");
		expired.setClearanceExpireTime(LocalDateTime.now().minusDays(1));
		GoodsSku inverted = sku("sku-3", "spu-1", "30.00", "35.00");

		when(goodsSkuMapper.selectList(any())).thenReturn(List.of(valid, expired, inverted));
		when(goodsSpuMapper.selectList(any())).thenReturn(List.of(spu));

		AppClearancePriceVO vo = service.clearancePrices(List.of("sku-1", "sku-2", "sku-3"));

		assertThat(vo.getItems()).hasSize(1);
		assertThat(vo.getItems().get(0).getSkuId()).isEqualTo("sku-1");
		// 旧 skuIds 契约不变：不额外回填 spuId
		assertThat(vo.getItems().get(0).getSpuId()).isNull();
		assertThat(vo.getItems().get(0).getClearancePrice()).isEqualByComparingTo("18.00");
		assertThat(vo.getItems().get(0).getSalesPrice()).isEqualByComparingTo("30.00");
	}

	@Test
	@DisplayName("按 spuIds 查价：返回 SPU 下有效 SKU 出清价，过滤到期/倒挂并按 SKU 稳定排序")
	void clearanceSpuPricesReturnsEffectiveSkuRows() {
		GoodsSpu spu = clearanceSpu("spu-1", "30.00");
		GoodsSku sku1 = sku("sku-1", "spu-1", "30.00", "18.00");
		GoodsSku sku2 = sku("sku-2", "spu-1", "30.00", "20.00");
		GoodsSku inverted = sku("sku-3", "spu-1", "30.00", "35.00");
		GoodsSku expired = sku("sku-4", "spu-1", "30.00", "18.00");
		expired.setClearanceExpireTime(LocalDateTime.now().minusDays(1));
		when(goodsSpuMapper.selectList(any())).thenReturn(List.of(spu));
		// 故意乱序返回，验证出参顺序稳定（请求 SPU 顺序内按 skuId 升序）
		when(goodsSkuMapper.selectList(any())).thenReturn(List.of(inverted, sku2, expired, sku1));
		injectRemotes(true, Map.of());

		AppClearancePriceVO vo = service.clearanceSpuPrices(List.of("spu-1"));

		assertThat(vo.getItems()).hasSize(2);
		assertThat(vo.getItems()).extracting(AppClearancePriceVO.Item::getSkuId).containsExactly("sku-1", "sku-2");
		AppClearancePriceVO.Item item = vo.getItems().get(0);
		assertThat(item.getSpuId()).isEqualTo("spu-1");
		assertThat(item.getSalesPrice()).isEqualByComparingTo("30.00");
		assertThat(item.getClearancePrice()).isEqualByComparingTo("18.00");
		// SKU 未配到期时回落 SPU 到期时间
		assertThat(item.getClearanceExpireTime()).isNotNull();
	}

	@Test
	@DisplayName("按 spuIds 查价：系统活动最终价覆盖基础价，且最终价倒挂不返回")
	void clearanceSpuPricesOverridesWithFinalPriceAndDropsInvertedFinalPrice() {
		GoodsSpu spu = clearanceSpu("spu-1", "30.00");
		GoodsSku sku1 = sku("sku-1", "spu-1", "30.00", "18.00");
		// 基础价 20 有效，但最终价 30 不低于售价，必须按倒挂丢弃
		GoodsSku sku2 = sku("sku-2", "spu-1", "30.00", "20.00");
		when(goodsSpuMapper.selectList(any())).thenReturn(List.of(spu));
		when(goodsSkuMapper.selectList(any())).thenReturn(List.of(sku1, sku2));
		injectRemotes(true, Map.of("sku-1", new BigDecimal("15.00"), "sku-2", new BigDecimal("30.00")));

		AppClearancePriceVO vo = service.clearanceSpuPrices(List.of("spu-1"));

		assertThat(vo.getItems()).hasSize(1);
		assertThat(vo.getItems().get(0).getSkuId()).isEqualTo("sku-1");
		assertThat(vo.getItems().get(0).getClearancePrice()).isEqualByComparingTo("15.00");
	}

	@Test
	@DisplayName("按 spuIds 查价：专区关闭返回空 items 且不查商品")
	void clearanceSpuPricesReturnsEmptyWhenZoneDisabled() {
		injectRemotes(false, Map.of());

		AppClearancePriceVO vo = service.clearanceSpuPrices(List.of("spu-1"));

		assertThat(vo.getItems()).isEmpty();
		verify(goodsSpuMapper, never()).selectList(any());
	}

	@Test
	@DisplayName("按 spuIds 查价：空/空白入参返回空 items")
	void clearanceSpuPricesReturnsEmptyForBlankInput() {
		AppClearancePriceVO vo = service.clearanceSpuPrices(List.of(" ", ""));

		assertThat(vo.getItems()).isEmpty();
	}

	@Test
	@DisplayName("按 spuIds 查价：最终价查询异常降级为基础有效价，不阻断展示")
	void clearanceSpuPricesFallsBackWhenFinalPriceQueryFails() {
		GoodsSpu spu = clearanceSpu("spu-1", "30.00");
		GoodsSku sku = sku("sku-1", "spu-1", "30.00", "18.00");
		when(goodsSpuMapper.selectList(any())).thenReturn(List.of(spu));
		when(goodsSkuMapper.selectList(any())).thenReturn(List.of(sku));
		RemoteMallZoneSettingService settingService = remoteSettingService(true);
		RemoteMallZonePriceService priceService = mock(RemoteMallZonePriceService.class);
		when(priceService.clearancePrices(eq(TENANT), anyList())).thenThrow(new IllegalStateException("promotion 不可用"));
		injectRemotes(settingService, priceService);

		AppClearancePriceVO vo = service.clearanceSpuPrices(List.of("spu-1"));

		assertThat(vo.getItems()).hasSize(1);
		assertThat(vo.getItems().get(0).getSkuId()).isEqualTo("sku-1");
		assertThat(vo.getItems().get(0).getClearancePrice()).isEqualByComparingTo("18.00");
	}

	@Test
	@DisplayName("单商品保存会员标签：不对未触碰的出清专区写幽灵历史")
	void saveTagsDoesNotWriteGhostClearanceHistory() {
		GoodsSpu spu = new GoodsSpu();
		spu.setId("spu-1");
		spu.setName("测试商品");
		spu.setStatus("1");
		spu.setIsMemberItem(0);
		spu.setIsClearanceItem(1);
		spu.setClearancePrice(new BigDecimal("10.00"));
		spu.setSalesPrice(new BigDecimal("30.00"));
		spu.setOriginalPrice(new BigDecimal("50.00"));
		when(goodsSpuMapper.selectById("spu-1")).thenReturn(spu);
		when(goodsSkuMapper.selectBySpuId("spu-1")).thenReturn(List.of());

		com.aryn.cloud.product.api.dto.GoodsZoneTagSaveDTO dto = new com.aryn.cloud.product.api.dto.GoodsZoneTagSaveDTO();
		dto.setMemberTag(true);
		dto.setMemberDiscountRate(new BigDecimal("0.9000"));
		service.saveTags("spu-1", dto);

		verify(goodsPriceHistoryService, never()).recordIfChanged(eq("spu-1"), any(), eq("clearance"), any(),
				any(), any(), any());
		verify(goodsPriceHistoryService).recordIfChanged(eq("spu-1"), any(), eq("member_rate"), any(), any(),
				any(), any());
	}

	@Test
	@DisplayName("单商品保存：显式清空会员规则不写幽灵出清历史，并落旧值→null")
	void saveTagsSupportsExplicitClearMemberRule() {
		GoodsSpu spu = new GoodsSpu();
		spu.setId("spu-1");
		spu.setName("测试商品");
		spu.setStatus("1");
		spu.setIsMemberItem(1);
		spu.setMemberDiscountRate(new BigDecimal("0.8500"));
		spu.setIsClearanceItem(1);
		spu.setClearancePrice(new BigDecimal("10.00"));
		spu.setSalesPrice(new BigDecimal("30.00"));
		spu.setOriginalPrice(new BigDecimal("50.00"));
		when(goodsSpuMapper.selectById("spu-1")).thenReturn(spu);
		when(goodsSkuMapper.selectBySpuId("spu-1")).thenReturn(List.of());

		com.aryn.cloud.product.api.dto.GoodsZoneTagSaveDTO dto = new com.aryn.cloud.product.api.dto.GoodsZoneTagSaveDTO();
		dto.setClearMemberRule(true);
		service.saveTags("spu-1", dto);

		verify(goodsPriceHistoryService).recordIfChanged(eq("spu-1"), any(), eq("member_rate"),
				eq(new BigDecimal("0.8500")), eq(null), any(), any());
		verify(goodsPriceHistoryService, never()).recordIfChanged(eq("spu-1"), any(), eq("clearance"), any(),
				any(), any(), any());
	}

	private void injectRemotes(boolean zoneEnabled, Map<String, BigDecimal> finalPrices) {
		RemoteMallZonePriceService priceService = mock(RemoteMallZonePriceService.class);
		when(priceService.clearancePrices(eq(TENANT), anyList())).thenReturn(finalPrices);
		injectRemotes(remoteSettingService(zoneEnabled), priceService);
	}

	private void injectRemotes(RemoteMallZoneSettingService settingService, RemoteMallZonePriceService priceService) {
		ReflectionTestUtils.setField(service, "remoteMallZoneSettingService", settingService);
		ReflectionTestUtils.setField(service, "remoteMallZonePriceService", priceService);
	}

	private RemoteMallZoneSettingService remoteSettingService(boolean zoneEnabled) {
		RemoteMallZoneSettingService settingService = mock(RemoteMallZoneSettingService.class);
		MallZoneSettingSnapshotVO setting = new MallZoneSettingSnapshotVO();
		setting.setZoneType(MallZoneSetting.ZONE_CLEARANCE);
		setting.setEnabled(zoneEnabled);
		when(settingService.getSetting(TENANT, MallZoneSetting.ZONE_CLEARANCE)).thenReturn(setting);
		return settingService;
	}

	private GoodsSpu clearanceSpu(String id, String salesPrice) {
		GoodsSpu spu = new GoodsSpu();
		spu.setId(id);
		spu.setStatus("1");
		spu.setIsClearanceItem(1);
		spu.setSalesPrice(new BigDecimal(salesPrice));
		spu.setOriginalPrice(new BigDecimal("100.00"));
		spu.setClearanceExpireTime(LocalDateTime.now().plusDays(1));
		return spu;
	}

	private GoodsZoneTagBatchDTO batchDTO(boolean overwrite) {
		GoodsZoneTagBatchDTO dto = new GoodsZoneTagBatchDTO();
		dto.setSpuIds(List.of("spu-1"));
		dto.setMemberTag(true);
		dto.setMemberDiscountRate(new BigDecimal("0.9000"));
		dto.setOverwriteSkuRules(overwrite);
		return dto;
	}

	private GoodsSku sku(String id, String spuId, String salesPrice, String clearancePrice) {
		GoodsSku sku = new GoodsSku();
		sku.setId(id);
		sku.setSpuId(spuId);
		sku.setSalesPrice(new BigDecimal(salesPrice));
		sku.setOriginalPrice(new BigDecimal("100.00"));
		sku.setClearancePrice(new BigDecimal(clearancePrice));
		return sku;
	}

}
