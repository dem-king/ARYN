package com.aryn.cloud.order.support;

import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 接龙 AI 助手的输出解析契约测试（不碰 HTTP，只测模型返回内容的防御式解析）。
 *
 * <p>守住两类线上必出的状况：模型爱包 ```json 围栏/带解释废话；选品幻觉
 * （skuId 不在候选集、行号不存在）必须被丢弃而不是当成真推荐。
 */
class ChainAiAssistantTest {

	private ReplenishImportMatchVO.Candidate candidate(String skuId, String name) {
		ReplenishImportMatchVO.Candidate candidate = new ReplenishImportMatchVO.Candidate();
		candidate.setSkuId(skuId);
		candidate.setName(name);
		return candidate;
	}

	@Test
	@DisplayName("解析输出：剥掉 ```json 围栏后逐行成 ParsedItem，数量兜底为 1")
	void parseChainContentStripsFence() {
		String content = """
				```json
				[
				  {"person": "任化东", "name": "青香蕉", "quantity": 3, "unit": "斤", "spec": null, "note": null},
				  {"person": "水手长", "name": "盐汽水", "quantity": null, "unit": "包", "spec": null, "note": "数量没写"},
				  {"person": null, "name": "  ", "quantity": 2, "unit": "斤", "spec": null, "note": null}
				]
				```""";

		List<ChainOrderTextParser.ParsedItem> items = ChainAiAssistant.parseChainContent(content);

		assertEquals(2, items.size(), "无名品的行要被丢弃");
		assertEquals("任化东", items.get(0).personName());
		assertEquals("青香蕉", items.get(0).goodsName());
		assertEquals(3, items.get(0).quantity());
		assertEquals(1, items.get(1).quantity(), "数量缺失按 1 兜底");
	}

	@Test
	@DisplayName("解析疑点按行产生：只有数量没识别出来（兜底 1）才提示，正常行不给警告")
	void parseChainContentWarningOnlyWhenQuantityMissing() {
		String content = """
				[
				  {"person": "任化东", "name": "红富士苹果", "quantity": 4, "unit": "斤", "spec": null, "note": null},
				  {"person": "水手长", "name": "盐汽水", "quantity": "六", "unit": "包", "spec": null, "note": null},
				  {"person": "大副", "name": "土豆", "quantity": 0, "unit": null, "spec": null, "note": null}
				]""";

		List<ChainOrderTextParser.ParsedItem> items = ChainAiAssistant.parseChainContent(content);

		assertEquals(3, items.size());
		assertEquals(null, items.get(0).warning(), "数量正常的行不带疑点，报告页不再满屏警告");
		assertEquals(1, items.get(1).quantity(), "非数字数量按 1 兜底");
		assertEquals("未识别到数量，默认 1，请核对", items.get(1).warning());
		assertEquals(1, items.get(2).quantity(), "非正数数量按 1 兜底");
		assertEquals("未识别到数量，默认 1，请核对", items.get(2).warning());
	}

	@Test
	@DisplayName("解析输出：模型带解释废话时截取 JSON 数组；完全非法则抛错回退")
	void parseChainContentDefensive() {
		List<ChainOrderTextParser.ParsedItem> items = ChainAiAssistant.parseChainContent(
				"解析结果如下：[{\"person\":\"任化东\",\"name\":\"橙子\",\"quantity\":4,\"unit\":\"斤\"}] 请核对");
		assertEquals(1, items.size());
		assertEquals("橙子", items.get(0).goodsName());
		assertEquals(null, items.get(0).warning(), "正常解析的行不带疑点");

		assertThrows(IllegalStateException.class, () -> ChainAiAssistant.parseChainContent("抱歉我无法解析"));
		assertThrows(IllegalStateException.class, () -> ChainAiAssistant.parseChainContent("[]"));
	}

	@Test
	@DisplayName("选品输出：skuId 不在候选集 / 行号不存在的幻觉推荐一律丢弃")
	void parsePickContentDropsHallucination() {
		Map<Integer, List<ReplenishImportMatchVO.Candidate>> candidatesByRowNo = new LinkedHashMap<>();
		candidatesByRowNo.put(1, List.of(candidate("sku-a", "赣南脐橙"), candidate("sku-b", "农夫NFC橙汁")));

		Map<Integer, String> picks = ChainAiAssistant.parsePickContent(
				"{\"picks\":[{\"rowNo\":1,\"skuId\":\"sku-b\"},{\"rowNo\":1,\"skuId\":\"sku-hallucinated\"},"
						+ "{\"rowNo\":99,\"skuId\":\"sku-a\"},{\"rowNo\":1,\"skuId\":null}]}",
				candidatesByRowNo);

		assertEquals(1, picks.size());
		assertEquals("sku-b", picks.get(1), "真实候选的推荐保留，幻觉与空推荐丢弃");
	}

	@Test
	@DisplayName("别名输出：行号不存在的丢弃；空/单字/超长/原名同义/重复词清洗；每行限 3 条")
	void parseSuggestContentCleansTerms() {
		Map<Integer, String> namesByRowNo = new LinkedHashMap<>();
		namesByRowNo.put(1, "西红柿");
		namesByRowNo.put(2, "地瓜");

		Map<Integer, List<String>> suggests = ChainAiAssistant.parseSuggestContent(
				"{\"suggests\":["
						+ "{\"rowNo\":1,\"terms\":[\"番茄\",\"西红柿\",\"番\",\"\",\"番 茄\",\"番茄酱\","
						+ "\"一个远远超过三十个字符上限的所谓别名显然是把说明文字整个搬进来了不应该采纳\"]},"
						+ "{\"rowNo\":2,\"terms\":[\"红薯\",\"红薯\"]},"
						+ "{\"rowNo\":99,\"terms\":[\"幻觉\"]}]}",
				namesByRowNo);

		// 「西红柿」与原名同义、「番」单字、超长词、「番 茄」与「番茄」规范化后重复，全部剔除
		assertEquals(List.of("番茄", "番茄酱"), suggests.get(1));
		assertEquals(List.of("红薯"), suggests.get(2), "重复别名只保留一条");
		assertFalse(suggests.containsKey(99), "行号不存在的建议一律丢弃");
	}

	@Test
	@DisplayName("别名输出：缺 suggests 字段或想不出别名时返回空；完全非法则抛错由调用方降级")
	void parseSuggestContentToleratesMissing() {
		Map<Integer, String> namesByRowNo = new LinkedHashMap<>();
		namesByRowNo.put(1, "稀有食材");
		assertTrue(ChainAiAssistant.parseSuggestContent("好的，{\"suggests\":[]}", namesByRowNo).isEmpty());
		assertTrue(ChainAiAssistant.parseSuggestContent(
				"{\"suggests\":[{\"rowNo\":1,\"terms\":[]}]}", namesByRowNo).isEmpty());
		// 与 parseChain 同契约：完全非法的输出抛错，调用方 catch 后维持人工搜索
		assertThrows(IllegalStateException.class,
				() -> ChainAiAssistant.parseSuggestContent("我无能为力", namesByRowNo));
	}

	@Test
	@DisplayName("JSON 块提取：围栏/前后废话/裸 JSON 三种形态都能截出")
	void extractJsonBlock() {
		assertEquals("[1,2]", ChainAiAssistant.extractJsonBlock("```json\n[1,2]\n```"));
		assertEquals("{\"a\":1}", ChainAiAssistant.extractJsonBlock("好的：{\"a\":1} 以上。"));
		assertEquals("[1]", ChainAiAssistant.extractJsonBlock("[1]"));
	}

	@Test
	@DisplayName("可用性：未配 key 或开关关闭都不可用，调用方走纯规则；别名召回默认关")
	void availabilityGate() {
		assertFalse(new ChainAiAssistant(true, false, "", "glm-4.7-flash",
				"https://open.bigmodel.cn/api/paas/v4", 20000).isAvailable());
		assertFalse(new ChainAiAssistant(false, false, "some-key", "glm-4.7-flash",
				"https://open.bigmodel.cn/api/paas/v4", 20000).isAvailable());
		assertTrue(new ChainAiAssistant(true, false, "some-key", "glm-4.7-flash",
				"https://open.bigmodel.cn/api/paas/v4", 20000).isAvailable());
		// 别名召回是独立开关（缺品备胎，默认关），与整体可用性无关
		assertTrue(new ChainAiAssistant(true, true, "some-key", "glm-4.7-flash",
				"https://open.bigmodel.cn/api/paas/v4", 20000).isAliasRecallEnabled());
	}

}
