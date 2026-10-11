package com.aryn.cloud.product.support;

import com.aryn.cloud.product.mapper.ReplenishImportMatchMapper;
import com.aryn.cloud.product.support.CatalogVectorRecallService.VectorMatch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 目录向量召回契约测试（mock embedding 客户端，不碰 HTTP）。
 *
 * <p>守住向量召回的三条行为边界：
 * <ul>
 *   <li><b>零字面重叠也能召回</b>：小番茄（语义向量）必须命中西红柿——这正是
 *       字面层够不着、向量层存在的意义；</li>
 *   <li><b>候选有界</b>：top-K、相似度阈值、单 SPU 占位上限三道闸都生效，
 *       多规格同名组不能把其它商品挤出候选；</li>
 *   <li><b>目录缓存</b>：同一租户重复召回不重建目录向量（TTL 内），
 *       embedding 只对查询品名发生。</li>
 * </ul>
 */
class CatalogVectorRecallServiceTest {

	private static final String TENANT = "tenant-1";

	private ReplenishImportMatchMapper mapper;

	private EmbeddingClient embeddingClient;

	private CatalogVectorRecallService service;

	@BeforeEach
	void setUp() {
		mapper = mock(ReplenishImportMatchMapper.class);
		embeddingClient = mock(EmbeddingClient.class);
		when(embeddingClient.isAvailable()).thenReturn(true);
		// 阈值 0.5 / top-K 6：测试里向量手工构造，距离拉开便于断言
		service = new CatalogVectorRecallService(mapper, embeddingClient, 60, 6, 0.5);
		// 目录向量化与查询向量化共用一个假编码器：按词映射到手工二维向量
		when(embeddingClient.embed(anyList())).thenAnswer(invocation -> {
			List<String> texts = invocation.getArgument(0);
			return texts.stream().map(CatalogVectorRecallServiceTest::vectorOf).toList();
		});
	}

	/** 手工二维语义空间：西红柿在 x 轴、苹果在 y 轴，小番茄贴近西红柿 */
	private static float[] vectorOf(String text) {
		return switch (text) {
			case "西红柿", "普罗旺斯西红柿" -> new float[]{1.0f, 0.0f};
			case "苹果", "烟台红富士苹果" -> new float[]{0.0f, 1.0f};
			case "小番茄" -> new float[]{0.95f, 0.05f};
			default -> new float[]{0.0f, 0.0f};
		};
	}

	private ReplenishImportSkuRow skuRow(String skuId, String spuId, String name) {
		ReplenishImportSkuRow row = new ReplenishImportSkuRow();
		row.setSkuId(skuId);
		row.setSpuId(spuId);
		row.setName(name);
		row.setSpuStatus("1");
		row.setSkuStatus("0");
		row.setStock(100);
		row.setSalesPrice(new BigDecimal("12.50"));
		return row;
	}

	@Test
	@DisplayName("余弦：同向为 1，正交为 0")
	void cosineBasics() {
		float[] x = CatalogVectorRecallService.normalize(new float[]{3.0f, 0.0f});
		float[] y = CatalogVectorRecallService.normalize(new float[]{0.0f, 7.0f});
		assertEquals(1.0, CatalogVectorRecallService.dot(x, x), 1e-6);
		assertEquals(0.0, CatalogVectorRecallService.dot(x, y), 1e-6);
	}

	@Test
	@DisplayName("零字面重叠也能召回：小番茄命中西红柿，苹果被阈值挡住")
	void recallsSemanticallyCloseNames() {
		when(mapper.selectCatalogRows(anyString(), anyInt())).thenReturn(List.of(
				skuRow("sku-tomato", "spu-tomato", "普罗旺斯西红柿"),
				skuRow("sku-apple", "spu-apple", "烟台红富士苹果")));

		List<List<VectorMatch>> recalled = service.recall(TENANT, List.of("小番茄"));

		assertEquals(1, recalled.size());
		assertEquals(1, recalled.get(0).size(), "苹果与查询近正交，应被阈值过滤");
		assertEquals("sku-tomato", recalled.get(0).get(0).row().getSkuId());
		assertTrue(recalled.get(0).get(0).score() > 0.9);
	}

	@Test
	@DisplayName("候选有界：单 SPU 占位溢出后由其它 SPU 补位，且总条数受 top-K 截断")
	void capsCandidatesByTopKAndPerSpu() {
		when(mapper.selectCatalogRows(anyString(), anyInt())).thenReturn(List.of(
				skuRow("sku-a1", "spu-a", "西红柿"),
				skuRow("sku-a2", "spu-a", "西红柿"),
				skuRow("sku-a3", "spu-a", "西红柿"),
				skuRow("sku-b", "spu-b", "西红柿")));

		CatalogVectorRecallService loose = new CatalogVectorRecallService(mapper, embeddingClient, 60, 4, 0.5);
		List<VectorMatch> matches = loose.recall(TENANT, List.of("小番茄")).get(0);
		// 同向量同分按 skuId 序：a1、a2 占满 spu-a 配额，a3 被单 SPU 上限挡住，b 补位
		assertEquals(3, matches.size());
		assertEquals(2, matches.stream().map(match -> match.row().getSpuId()).distinct().count());

		CatalogVectorRecallService tight = new CatalogVectorRecallService(mapper, embeddingClient, 60, 2, 0.5);
		List<VectorMatch> capped = tight.recall(TENANT, List.of("小番茄")).get(0);
		assertEquals(2, capped.size(), "top-K=2 截断");
	}

	@Test
	@DisplayName("目录缓存：TTL 内重复召回不重建目录向量，只对查询品名做 embedding")
	void cachesCatalogWithinTtl() {
		when(mapper.selectCatalogRows(anyString(), anyInt())).thenReturn(List.of(
				skuRow("sku-tomato", "spu-tomato", "普罗旺斯西红柿")));

		service.recall(TENANT, List.of("小番茄"));
		service.recall(TENANT, List.of("小番茄"));

		verify(mapper, times(1)).selectCatalogRows(anyString(), anyInt());
	}

	@Test
	@DisplayName("embedding 不可用时召回整体跳过，不碰目录查询")
	void skipsWhenClientUnavailable() {
		when(embeddingClient.isAvailable()).thenReturn(false);

		List<List<VectorMatch>> recalled = service.recall(TENANT, List.of("小番茄"));

		assertEquals(1, recalled.size());
		assertTrue(recalled.get(0).isEmpty());
		verify(mapper, times(0)).selectCatalogRows(anyString(), anyInt());
	}

}
