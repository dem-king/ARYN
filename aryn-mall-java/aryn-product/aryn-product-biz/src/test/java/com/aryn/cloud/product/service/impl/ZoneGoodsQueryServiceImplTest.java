package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.dto.ZoneGoodsIdsDTO;
import com.aryn.cloud.product.api.dto.ZoneGoodsQueryDTO;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.ZoneGoodsIdsVO;
import com.aryn.cloud.product.api.vo.ZoneGoodsRowVO;
import com.aryn.cloud.product.mapper.ZoneGoodsQueryMapper;
import com.aryn.cloud.product.service.support.GoodsZoneTagStateResolver;
import com.aryn.cloud.promotion.api.remote.RemoteMallZoneSettingService;
import com.aryn.cloud.promotion.api.vo.MallZoneSettingSnapshotVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 专区商品查询契约：固定 id 升序分页（映射到 ZoneGoodsRowVO + 生效状态）、按筛选取 ID 上限、SKU 精调计数。
 */
class ZoneGoodsQueryServiceImplTest {

	private static final String TENANT = "tenant-1";

	private ZoneGoodsQueryMapper zoneGoodsQueryMapper;

	private RemoteMallZoneSettingService remoteMallZoneSettingService;

	private ZoneGoodsQueryServiceImpl service;

	@BeforeEach
	void setUp() {
		zoneGoodsQueryMapper = mock(ZoneGoodsQueryMapper.class);
		remoteMallZoneSettingService = mock(RemoteMallZoneSettingService.class);
		service = new ZoneGoodsQueryServiceImpl(zoneGoodsQueryMapper, new GoodsZoneTagStateResolver());
		ReflectionTestUtils.setField(service, "remoteMallZoneSettingService", remoteMallZoneSettingService);
		ArynTenantContextHolder.setTenantId(TENANT);
	}

	@AfterEach
	void tearDown() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	@DisplayName("分页：映射行字段 + 用专区默认折扣算生效价 + SKU 精调计数")
	void pageMapsRowsAndComputesEffectiveState() {
		GoodsSpu spu = spu("spu-1", "1");
		spu.setMemberDiscountRate(null);
		Page<GoodsSpu> raw = new Page<>(1, 10);
		raw.setRecords(List.of(spu));
		raw.setTotal(1);
		when(zoneGoodsQueryMapper.selectZonePage(any(), any())).thenReturn(raw);
		when(zoneGoodsQueryMapper.selectSkuZoneRows(any())).thenReturn(List.of(skuWithOverride("sku-1")));
		when(remoteMallZoneSettingService.getSetting(TENANT, "member")).thenReturn(zoneSetting("0.9000"));

		ZoneGoodsQueryDTO query = new ZoneGoodsQueryDTO();
		query.setZoneType("member");
		IPage<ZoneGoodsRowVO> result = service.page(query);

		assertThat(result.getTotal()).isEqualTo(1);
		ZoneGoodsRowVO row = result.getRecords().get(0);
		assertThat(row.getId()).isEqualTo("spu-1");
		assertThat(row.getZoneType()).isEqualTo("member");
		assertThat(row.getSkuOverrideCount()).isEqualTo(1);
		assertThat(row.getState()).isEqualTo("NORMAL");
		assertThat(row.getStateLabel()).isEqualTo("生效中");
		// 39.9 × 0.9 = 35.91（专区默认折扣兜底）
		assertThat(row.getEffectivePrice()).isEqualByComparingTo("35.91");
	}

	@Test
	@DisplayName("分页：已下架商品状态优先为 OFF_SHELF 且无生效价")
	void pageMarksOffShelfProduct() {
		GoodsSpu spu = spu("spu-1", "0");
		Page<GoodsSpu> raw = new Page<>(1, 10);
		raw.setRecords(List.of(spu));
		raw.setTotal(1);
		when(zoneGoodsQueryMapper.selectZonePage(any(), any())).thenReturn(raw);
		when(zoneGoodsQueryMapper.selectSkuZoneRows(any())).thenReturn(List.of());

		ZoneGoodsQueryDTO query = new ZoneGoodsQueryDTO();
		query.setZoneType("clearance");
		IPage<ZoneGoodsRowVO> result = service.page(query);

		ZoneGoodsRowVO row = result.getRecords().get(0);
		assertThat(row.getState()).isEqualTo("OFF_SHELF");
		assertThat(row.getEffectivePrice()).isNull();
	}

	@Test
	@DisplayName("分页：专区类型非法抛业务异常")
	void pageRejectsUnknownZoneType() {
		ZoneGoodsQueryDTO query = new ZoneGoodsQueryDTO();
		query.setZoneType("not_member");

		ArynBusinessException ex = catchThrowableOfType(() -> service.page(query),
				ArynBusinessException.class);
		assertThat(ex).isNotNull();
		assertThat(ex.getMsg()).contains("member / clearance");
	}

	@Test
	@DisplayName("取 ID：limit 收敛到 1000，返回 total/ids/truncated")
	void idsClampsLimitAndReportsTruncated() {
		ZoneGoodsIdsDTO query = new ZoneGoodsIdsDTO();
		query.setZoneType("member");
		query.setLimit(5000);
		when(zoneGoodsQueryMapper.countZoneIds(query)).thenReturn(18L);
		when(zoneGoodsQueryMapper.selectZoneIds(query, 1000)).thenReturn(List.of("spu-1", "spu-2"));

		ZoneGoodsIdsVO result = service.ids(query);

		assertThat(result.getTotal()).isEqualTo(18L);
		assertThat(result.getIds()).containsExactly("spu-1", "spu-2");
		assertThat(result.getTruncated()).isTrue();
		verify(zoneGoodsQueryMapper).selectZoneIds(query, 1000);
	}

	@Test
	@DisplayName("取 ID：新增 not_member/not_clearance 过滤值可用")
	void idsAcceptsNotZoneTypes() {
		ZoneGoodsIdsDTO query = new ZoneGoodsIdsDTO();
		query.setZoneType("not_clearance");
		when(zoneGoodsQueryMapper.countZoneIds(query)).thenReturn(0L);

		ZoneGoodsIdsVO result = service.ids(query);

		assertThat(result.getTotal()).isZero();
		assertThat(result.getIds()).isEmpty();
		assertThat(result.getTruncated()).isFalse();
	}

	@Test
	@DisplayName("取 ID：非法 zoneType 抛业务异常")
	void idsRejectsUnknownZoneType() {
		ZoneGoodsIdsDTO query = new ZoneGoodsIdsDTO();
		query.setZoneType("bad");

		ArynBusinessException ex = catchThrowableOfType(() -> service.ids(query),
				ArynBusinessException.class);
		assertThat(ex).isNotNull();
		assertThat(ex.getMsg()).contains("not_member");
	}

	@Test
	@DisplayName("SKU 精调计数：按 spuId 汇总存在任一专区覆盖的 SKU 数")
	void skuOverrideCountsGroupsBySpu() {
		GoodsSku plain = new GoodsSku();
		plain.setId("sku-2");
		plain.setSpuId("spu-1");
		when(zoneGoodsQueryMapper.selectSkuZoneRows(any())).thenReturn(List.of(skuWithOverride("sku-1"), plain));

		Map<String, Integer> counts = service.skuOverrideCounts(List.of("spu-1", "spu-2"));

		assertThat(counts).containsEntry("spu-1", 1).containsEntry("spu-2", 0);
	}

	@Test
	@DisplayName("SKU 精调计数：空入参直接返回空 Map，不查库")
	void skuOverrideCountsEmptyInput() {
		assertThat(service.skuOverrideCounts(List.of())).isEmpty();
		verify(zoneGoodsQueryMapper, org.mockito.Mockito.never()).selectSkuZoneRows(any());
	}

	private GoodsSpu spu(String spuId, String status) {
		GoodsSpu spu = new GoodsSpu();
		spu.setId(spuId);
		spu.setName("测试商品");
		spu.setSubTitle("副标题");
		spu.setStatus(status);
		spu.setStock(100);
		spu.setSalesPrice(new BigDecimal("39.9"));
		spu.setOriginalPrice(new BigDecimal("59.9"));
		spu.setIsMemberItem(1);
		spu.setIsClearanceItem(0);
		return spu;
	}

	private GoodsSku skuWithOverride(String skuId) {
		GoodsSku sku = new GoodsSku();
		sku.setId(skuId);
		sku.setSpuId("spu-1");
		sku.setStatus("0");
		sku.setSalesPrice(new BigDecimal("39.9"));
		sku.setOriginalPrice(new BigDecimal("59.9"));
		sku.setIsMemberItem(1);
		sku.setMemberDiscountRate(new BigDecimal("0.8500"));
		return sku;
	}

	private MallZoneSettingSnapshotVO zoneSetting(String defaultRate) {
		MallZoneSettingSnapshotVO vo = new MallZoneSettingSnapshotVO();
		vo.setZoneType("member");
		vo.setEnabled(true);
		vo.setDefaultDiscountRate(defaultRate == null ? null : new BigDecimal(defaultRate));
		return vo;
	}

}
