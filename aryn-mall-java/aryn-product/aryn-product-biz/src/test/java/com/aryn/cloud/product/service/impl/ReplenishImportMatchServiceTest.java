package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.product.api.dto.ReplenishImportMatchDTO;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.vo.ReplenishCatalogRowVO;
import com.aryn.cloud.product.api.vo.ReplenishCatalogVO;
import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;
import com.aryn.cloud.product.mapper.ReplenishImportMatchMapper;
import com.aryn.cloud.product.support.ReplenishImportSkuRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 补给单导入匹配与模板目录导出契约测试。
 *
 * <p>船供资料下线（2026-09-29）后「商品编码」列即 SKU 编号（{@code goods_sku.id}），
 * 这里守住两类最容易静默出错的缺陷：
 * <ul>
 *   <li><b>导出的编码自己解析不出来</b>：导出写进「商品编码」列的值必须能被
 *       匹配链路按同一键查回，否则客户只改数量回传也是整份「全部未匹配」；</li>
 *   <li><b>模板把不可售商品列进去</b>：下架商品写进模板，客户填了也只会被标「已下架」。</li>
 * </ul>
 */
class ReplenishImportMatchServiceTest {

	private static final String TENANT = "tenant-1";

	private ReplenishImportMatchMapper replenishImportMatchMapper;

	private ReplenishImportMatchServiceImpl service;

	@BeforeEach
	void setUp() {
		replenishImportMatchMapper = mock(ReplenishImportMatchMapper.class);
		service = new ReplenishImportMatchServiceImpl(replenishImportMatchMapper);
	}

	private ReplenishImportSkuRow skuRow(String skuId, String name) {
		ReplenishImportSkuRow row = new ReplenishImportSkuRow();
		row.setSkuId(skuId);
		row.setSpuId("spu-" + skuId);
		row.setName(name);
		row.setSpuStatus("1");
		row.setSkuStatus("0");
		row.setStock(100);
		row.setSalesPrice(new BigDecimal("12.50"));
		return row;
	}

	private ReplenishImportMatchDTO request(String code, String name) {
		ReplenishImportMatchDTO dto = new ReplenishImportMatchDTO();
		dto.setRowNo(1);
		dto.setCode(code);
		dto.setName(name);
		dto.setQuantity(4);
		return dto;
	}

	@Test
	@DisplayName("编码列即 SKU 编号：按编码精确唯一命中，matchType=CODE")
	void matchesCodeAsSkuId() {
		ReplenishImportSkuRow hit = skuRow("975000000000000001", "船用洗手液");
		when(replenishImportMatchMapper.selectRowsBySkuIds(anyString(), anyList())).thenReturn(List.of(hit));

		List<ReplenishImportMatchVO> results = service.matchRows(TENANT, List.of(request("975000000000000001", null)));

		assertEquals(1, results.size());
		assertEquals("CODE", results.get(0).getMatchType(), "目录模板回传的编码必须能按 SKU 编号命中");
		assertEquals(hit.getSkuId(), results.get(0).getSkuId());
		assertNull(results.get(0).getMoq(), "船供包装资料下线后 MOQ 恒空，由订单域按 1 兜底");
	}

	@Test
	@DisplayName("编码列不是合法 SKU 编号时回落品名匹配，matchType=NAME")
	void fallsBackToNameWhenCodeUnknown() {
		when(replenishImportMatchMapper.selectRowsBySkuIds(anyString(), anyList())).thenReturn(List.of());
		when(replenishImportMatchMapper.selectRowsByExactNames(anyString(), anyList()))
			.thenReturn(List.of(skuRow("sku-1", "鲜牛奶 950ml")));

		List<ReplenishImportMatchVO> results = service.matchRows(TENANT, List.of(request("NOT-A-SKU", "鲜牛奶 950ml")));

		assertEquals(1, results.size());
		assertEquals("NAME", results.get(0).getMatchType(), "编码查不到时必须落到品名兜底，而不是直接未匹配");
		assertEquals("sku-1", results.get(0).getSkuId());
	}

	@Test
	@DisplayName("品名命中多个 SKU 时进歧义待选，不自动入单")
	void ambiguousNameGivesCandidates() {
		when(replenishImportMatchMapper.selectRowsBySkuIds(anyString(), anyList())).thenReturn(List.of());
		when(replenishImportMatchMapper.selectRowsByExactNames(anyString(), anyList()))
			.thenReturn(List.of(skuRow("sku-1", "同款商品"), skuRow("sku-2", "同款商品")));

		List<ReplenishImportMatchVO> results = service.matchRows(TENANT, List.of(request(null, "同款商品")));

		assertEquals("AMBIGUOUS", results.get(0).getMatchType(), "同品名多 SKU 不能替客户猜，必须人工补选");
		assertEquals(2, results.get(0).getCandidates().size());
	}

	@Test
	@DisplayName("目录导出：编码列写 SKU 编号并带分类，保证回查闭环")
	void exportCatalogWritesSkuIdAsCode() {
		ReplenishImportSkuRow row = skuRow("975000000000000001", "鲜牛奶 950ml");
		row.setCategoryFirstName("乳品烘焙");
		row.setCategorySecondName("牛奶");
		when(replenishImportMatchMapper.selectCatalogRows(anyString(), anyInt())).thenReturn(List.of(row));
		when(replenishImportMatchMapper.countCatalogRows(anyString())).thenReturn(1);

		ReplenishCatalogVO catalog = service.exportCatalog(TENANT, 2000);

		assertEquals(1, catalog.getRows().size());
		ReplenishCatalogRowVO exported = catalog.getRows().get(0);
		assertEquals("975000000000000001", exported.getCode(), "导出的编码必须与 matchRows 的回查键一致");
		assertEquals("乳品烘焙/牛奶", exported.getCategoryName(), "模板要按分类铺开，分类名不能为空");
		assertFalse(catalog.isTruncated());
	}

	@Test
	@DisplayName("目录导出：超出上限时如实回传总数与截断标记")
	void exportCatalogReportsTruncation() {
		ReplenishImportSkuRow row = skuRow("sku-1", "商品");
		when(replenishImportMatchMapper.selectCatalogRows(anyString(), anyInt())).thenReturn(List.of(row));
		when(replenishImportMatchMapper.countCatalogRows(anyString())).thenReturn(5000);

		ReplenishCatalogVO catalog = service.exportCatalog(TENANT, 2000);

		assertTrue(catalog.isTruncated(), "静默截断会让客户以为「我的商品没建档」");
		assertEquals(5000, catalog.getTotalCount());
		assertEquals(1, catalog.getRows().size());
	}

	@Test
	@DisplayName("规格描述口径与匹配比对一致：多规格值按「；」拼接")
	void specTextMatchesDisplayConvention() {
		ReplenishImportSkuRow row = skuRow("sku-1", "鲜牛奶");
		GoodsSku.Specs first = new GoodsSku.Specs();
		first.setSpecsValueName("950ml");
		GoodsSku.Specs second = new GoodsSku.Specs();
		second.setSpecsValueName("瓶装");
		row.setSpecsArr(List.of(first, second));

		assertEquals("950ml；瓶装", ReplenishImportMatchServiceImpl.joinSpecs(row));
		// 无规格值返回 null：空规格与「无规格」同义，不能拼成空串去比对
		ReplenishImportSkuRow empty = skuRow("sku-2", "无规格商品");
		assertNull(ReplenishImportMatchServiceImpl.joinSpecs(empty));
	}

}
