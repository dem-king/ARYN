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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
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

	private com.aryn.cloud.product.support.CatalogVectorRecallService catalogVectorRecallService;

	@BeforeEach
	void setUp() {
		replenishImportMatchMapper = mock(ReplenishImportMatchMapper.class);
		// 默认 mock isAvailable=false：既有字面匹配用例不受向量层影响
		catalogVectorRecallService = mock(com.aryn.cloud.product.support.CatalogVectorRecallService.class);
		service = new ReplenishImportMatchServiceImpl(replenishImportMatchMapper, catalogVectorRecallService);
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

	/**
	 * 关键词查询按入参区分桩：全名关键词（如「青香蕉」）与二字组关键词
	 * （如「青香」「香蕉」）走同一个 mapper 方法，按包含关系分辨。
	 */
	private void stubKeywordQuery(List<ReplenishImportSkuRow> fullNameHits,
			List<ReplenishImportSkuRow> bigramHits, String fullName) {
		when(replenishImportMatchMapper.selectRowsByKeywords(anyString(), anyList())).thenAnswer(invocation -> {
			List<String> keywords = invocation.getArgument(1);
			return keywords.contains(fullName) ? fullNameHits : bigramHits;
		});
	}

	private ReplenishImportMatchDTO fuzzyRequest(String name) {
		ReplenishImportMatchDTO dto = request(null, name);
		dto.setFuzzy(true);
		return dto;
	}

	@Test
	@DisplayName("模糊匹配：修饰词夹心（青香蕉→国产香蕉）给一键候选，不自动定品")
	void fuzzySuggestsSandwichName() {
		ReplenishImportSkuRow banana = skuRow("sku-banana", "国产香蕉");
		when(replenishImportMatchMapper.selectRowsBySkuIds(anyString(), anyList())).thenReturn(List.of());
		when(replenishImportMatchMapper.selectRowsByExactNames(anyString(), anyList())).thenReturn(List.of());
		stubKeywordQuery(List.of(), List.of(banana), "青香蕉");

		List<ReplenishImportMatchVO> results = service.matchRows(TENANT, List.of(fuzzyRequest("青香蕉")));

		// 0.5 的重叠混着「老冰糖→黄冰糖」这类真不同的商品，自动定品会错单；
		// 候选一键采用足够顺手，判断留给人
		assertEquals("AMBIGUOUS", results.get(0).getMatchType(), "夹心品名必须进候选让人一键确认");
		assertEquals("sku-banana", results.get(0).getCandidates().get(0).getSkuId());
	}

	@Test
	@DisplayName("模糊匹配：强重叠且唯一最佳 → 自动命中（红富士冰糖心→红富士苹果冰糖心）")
	void fuzzyAutoMatchesStrongOverlap() {
		ReplenishImportSkuRow apple = skuRow("sku-apple", "红富士苹果冰糖心 4斤装");
		when(replenishImportMatchMapper.selectRowsBySkuIds(anyString(), anyList())).thenReturn(List.of());
		when(replenishImportMatchMapper.selectRowsByExactNames(anyString(), anyList())).thenReturn(List.of());
		stubKeywordQuery(List.of(), List.of(apple), "红富士冰糖心");

		List<ReplenishImportMatchVO> results = service.matchRows(TENANT, List.of(fuzzyRequest("红富士冰糖心")));

		assertEquals("NAME", results.get(0).getMatchType(), "0.8 的核心词重叠是同一商品，不该再让人点一遍");
		assertEquals("sku-apple", results.get(0).getSkuId());
	}

	@Test
	@DisplayName("模糊匹配：重叠不够自动阈值（小番茄→番茄酱）只给候选，不替人定品")
	void fuzzyBelowAutoThresholdGivesCandidates() {
		when(replenishImportMatchMapper.selectRowsBySkuIds(anyString(), anyList())).thenReturn(List.of());
		when(replenishImportMatchMapper.selectRowsByExactNames(anyString(), anyList())).thenReturn(List.of());
		stubKeywordQuery(List.of(), List.of(skuRow("sku-sauce", "番茄酱")), "小番茄");

		List<ReplenishImportMatchVO> results = service.matchRows(TENANT, List.of(fuzzyRequest("小番茄")));

		assertEquals("AMBIGUOUS", results.get(0).getMatchType(), "0.5 的重叠自动定品会错到别的商品上");
		assertEquals(1, results.get(0).getCandidates().size());
		assertEquals("sku-sauce", results.get(0).getCandidates().get(0).getSkuId());
	}

	@Test
	@DisplayName("模糊匹配：多候选同分（同品名多规格）不自动定品")
	void fuzzyTieDoesNotAutoMatch() {
		when(replenishImportMatchMapper.selectRowsBySkuIds(anyString(), anyList())).thenReturn(List.of());
		when(replenishImportMatchMapper.selectRowsByExactNames(anyString(), anyList())).thenReturn(List.of());
		stubKeywordQuery(List.of(),
				List.of(skuRow("sku-1", "手抓卷饼皮"), skuRow("sku-2", "手抓卷饼皮")), "卷饼皮");

		List<ReplenishImportMatchVO> results = service.matchRows(TENANT, List.of(fuzzyRequest("卷饼皮")));

		assertEquals("AMBIGUOUS", results.get(0).getMatchType(), "同分说明规格没收敛，必须人工选");
		assertEquals(2, results.get(0).getCandidates().size());
	}

	@Test
	@DisplayName("Excel 导入不受模糊匹配影响：未启用 fuzzy 的行为与原来一致")
	void nonFuzzyRowsSkipFuzzyTier() {
		when(replenishImportMatchMapper.selectRowsBySkuIds(anyString(), anyList())).thenReturn(List.of());
		when(replenishImportMatchMapper.selectRowsByExactNames(anyString(), anyList())).thenReturn(List.of());
		// 二字组召回查到了候选，但行未启用 fuzzy：必须维持「未匹配」原判
		stubKeywordQuery(List.of(), List.of(skuRow("sku-banana", "国产香蕉")), "青香蕉");

		List<ReplenishImportMatchVO> results = service.matchRows(TENANT, List.of(request(null, "青香蕉")));

		assertEquals("NONE", results.get(0).getMatchType(), "fuzzy 未启用时不得悄悄改 Excel 行为");
		assertNull(results.get(0).getSkuId());
	}

	@Test
	@DisplayName("变体剥离：尾部形容词不参与匹配（卤牛腱子优质 → 原切牛腱子肉候选）")
	void fuzzyStripsTrailingAdjective() {
		ReplenishImportSkuRow beef = skuRow("sku-beef", "原切牛腱子肉");
		when(replenishImportMatchMapper.selectRowsBySkuIds(anyString(), anyList())).thenReturn(List.of());
		when(replenishImportMatchMapper.selectRowsByExactNames(anyString(), anyList())).thenReturn(List.of());
		stubKeywordQuery(List.of(), List.of(beef), "卤牛腱子优质");

		List<ReplenishImportMatchVO> results = service.matchRows(TENANT, List.of(fuzzyRequest("卤牛腱子优质")));

		// 剥出「卤牛腱子」后与「原切牛腱子肉」核心词重叠 0.67：给候选不自动定品
		// （卤味和原切是不是同一款，人判断）
		assertEquals("AMBIGUOUS", results.get(0).getMatchType());
		assertEquals("sku-beef", results.get(0).getCandidates().get(0).getSkuId());
	}

	@Test
	@DisplayName("变体剥离：尾部规格不参与匹配（上海盐汽水600ml*24瓶 → 上海盐汽水可命中）")
	void fuzzyStripsTrailingSpec() {
		ReplenishImportSkuRow soda = skuRow("sku-soda", "上海盐汽水");
		when(replenishImportMatchMapper.selectRowsBySkuIds(anyString(), anyList())).thenReturn(List.of());
		when(replenishImportMatchMapper.selectRowsByExactNames(anyString(), anyList())).thenReturn(List.of());
		stubKeywordQuery(List.of(), List.of(soda), "上海盐汽水600ml*24瓶");

		List<ReplenishImportMatchVO> results = service.matchRows(TENANT, List.of(fuzzyRequest("上海盐汽水600ml*24瓶")));

		// 剥出「上海盐汽水」后与同名商品 1.0 重叠：唯一最高分自动命中
		assertEquals("NAME", results.get(0).getMatchType(), "规格尾巴不该挡住同名商品");
		assertEquals("sku-soda", results.get(0).getSkuId());
	}

	@Test
	@DisplayName("单字兜底：口语短名零二字组重叠时按共享汉字给候选（橙子 → 赣南脐橙）")
	void charFallbackSuggestsForColloquialShortName() {
		ReplenishImportSkuRow orange = skuRow("sku-orange", "赣南脐橙");
		when(replenishImportMatchMapper.selectRowsBySkuIds(anyString(), anyList())).thenReturn(List.of());
		when(replenishImportMatchMapper.selectRowsByExactNames(anyString(), anyList())).thenReturn(List.of());
		// 召回区分：全名关键词「橙子」查不到任何商品；单字「橙」召回脐橙与橙汁家族
		when(replenishImportMatchMapper.selectRowsByKeywords(anyString(), anyList())).thenAnswer(invocation -> {
			List<String> keywords = invocation.getArgument(1);
			return keywords.contains("橙") ? List.of(orange) : List.of();
		});

		List<ReplenishImportMatchVO> results = service.matchRows(TENANT, List.of(fuzzyRequest("橙子")));

		// 「橙子」与「赣南脐橙」连二字组都不共享，靠共享汉字「橙」给候选；
		// 单字重叠噪声大（橙汁/橙味全家桶都会进来），只建议不自动
		assertEquals("AMBIGUOUS", results.get(0).getMatchType());
		assertEquals("sku-orange", results.get(0).getCandidates().get(0).getSkuId());
	}

	@Test
	@DisplayName("向量召回：字面层全落空的模糊行用语义相似度补候选，matchType 保持 NONE")
	void vectorRecallFillsLexicallyHopelessRows() {
		// 小番茄与西红柿零共享字符，字面层必然 NONE
		when(replenishImportMatchMapper.selectRowsBySkuIds(anyString(), anyList())).thenReturn(List.of());
		when(replenishImportMatchMapper.selectRowsByExactNames(anyString(), anyList())).thenReturn(List.of());
		when(replenishImportMatchMapper.selectRowsByKeywords(anyString(), anyList())).thenReturn(List.of());
		ReplenishImportSkuRow tomato = skuRow("sku-tomato", "普罗旺斯西红柿");
		com.aryn.cloud.product.support.CatalogVectorRecallService.VectorMatch match =
				new com.aryn.cloud.product.support.CatalogVectorRecallService.VectorMatch(tomato, 0.82);
		when(catalogVectorRecallService.isAvailable()).thenReturn(true);
		when(catalogVectorRecallService.recall(anyString(), anyList())).thenReturn(List.of(List.of(match)));

		List<ReplenishImportMatchVO> results = service.matchRows(TENANT, List.of(fuzzyRequest("小番茄")));

		// 候选落到报告行但行本身仍是未匹配：语义相近 ≠ 同一种商品，定品由人
		assertEquals("NONE", results.get(0).getMatchType());
		assertEquals("sku-tomato", results.get(0).getCandidates().get(0).getSkuId());
	}

	@Test
	@DisplayName("向量召回：未配置（isAvailable=false）时整层跳过，不发起召回")
	void vectorRecallSkippedWhenUnavailable() {
		when(replenishImportMatchMapper.selectRowsBySkuIds(anyString(), anyList())).thenReturn(List.of());
		when(replenishImportMatchMapper.selectRowsByExactNames(anyString(), anyList())).thenReturn(List.of());
		when(replenishImportMatchMapper.selectRowsByKeywords(anyString(), anyList())).thenReturn(List.of());

		List<ReplenishImportMatchVO> results = service.matchRows(TENANT, List.of(fuzzyRequest("小番茄")));

		assertEquals("NONE", results.get(0).getMatchType());
		assertNull(results.get(0).getCandidates());
		verify(catalogVectorRecallService, times(0)).recall(anyString(), anyList());
	}

	@Test
	@DisplayName("向量召回：Excel 行（fuzzy=false）不参与，编码命中的行不受影响")
	void vectorRecallOnlyForFuzzyRows() {
		// 编码列即 SKU 编号：skuRow 的 skuId 必须与编码值一致，才能被编码反查命中
		ReplenishImportSkuRow hit = skuRow("975000000000000009", "船用洗手液");
		when(replenishImportMatchMapper.selectRowsBySkuIds(anyString(), anyList())).thenReturn(List.of(hit));
		when(catalogVectorRecallService.isAvailable()).thenReturn(true);
		when(catalogVectorRecallService.recall(anyString(), anyList())).thenReturn(List.of(List.of()));

		ReplenishImportMatchDTO excelRow = request("975000000000000009", null);
		List<ReplenishImportMatchVO> results = service.matchRows(TENANT, List.of(excelRow));

		// 编码唯一命中：不需要也不应该走向量
		assertEquals("CODE", results.get(0).getMatchType());
		assertEquals("975000000000000009", results.get(0).getSkuId());
		verify(catalogVectorRecallService, times(0)).recall(anyString(), anyList());
	}

}
