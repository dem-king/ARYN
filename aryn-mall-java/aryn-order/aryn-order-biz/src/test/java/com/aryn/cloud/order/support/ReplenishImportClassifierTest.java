package com.aryn.cloud.order.support;

import com.aryn.cloud.order.api.entity.SharedCartImportRow;
import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 补给单导入报告分类口径测试。
 *
 * <p>这些断言同时守两类缺陷：
 * <ul>
 *   <li>把「已下架」误判成「未匹配」——用户会一直补选不到，且看不出原因；</li>
 *   <li>把「数量异常」与「超库存」混为一谈——报告的动作会指错（该调数量却让改规格）。</li>
 * </ul>
 */
class ReplenishImportClassifierTest {

	private ReplenishImportMatchVO matched(int stock, int moq, int stepQty, String spec) {
		ReplenishImportMatchVO vo = new ReplenishImportMatchVO();
		vo.setMatchType("CODE");
		vo.setSkuId("sku-1");
		vo.setSpuId("spu-1");
		vo.setName("鲜牛奶 950ml");
		vo.setSpec(spec);
		vo.setStock(stock);
		vo.setMoq(moq);
		vo.setStepQty(stepQty);
		vo.setSkuStatus("0");
		vo.setSpuStatus("1");
		return vo;
	}

	@Test
	@DisplayName("未命中进未匹配，不猜 SKU")
	void unmatchedWhenNoMatch() {
		var result = ReplenishImportClassifier.classify(null, "950ml", 3);
		assertEquals(SharedCartImportRow.RESULT_UNMATCHED, result.resultType());
	}

	@Test
	@DisplayName("多规格歧义进未匹配并提示人工确认，不自动入单")
	void ambiguousIsUnmatched() {
		ReplenishImportMatchVO vo = matched(10, 1, 1, "950ml/瓶");
		vo.setMatchType("AMBIGUOUS");
		vo.setSkuId(null);
		var result = ReplenishImportClassifier.classify(vo, null, 3);
		assertEquals(SharedCartImportRow.RESULT_UNMATCHED, result.resultType());
	}

	@Test
	@DisplayName("SKU 或 SPU 下架单独成类，不与未匹配混算")
	void offShelfIsSeparate() {
		ReplenishImportMatchVO skuOff = matched(10, 1, 1, "950ml/瓶");
		skuOff.setSkuStatus("1");
		assertEquals(SharedCartImportRow.RESULT_OFF_SHELF,
				ReplenishImportClassifier.classify(skuOff, null, 3).resultType());

		ReplenishImportMatchVO spuOff = matched(10, 1, 1, "950ml/瓶");
		spuOff.setSpuStatus("0");
		assertEquals(SharedCartImportRow.RESULT_OFF_SHELF,
				ReplenishImportClassifier.classify(spuOff, null, 3).resultType());
	}

	@Test
	@DisplayName("数量为空/低于MOQ/非步长整数倍都进数量异常，并给出建议值")
	void invalidQuantity() {
		ReplenishImportMatchVO vo = matched(100, 4, 2, "950ml/瓶");
		assertEquals(SharedCartImportRow.RESULT_INVALID_QTY,
				ReplenishImportClassifier.classify(vo, null, null).resultType());
		assertEquals(SharedCartImportRow.RESULT_INVALID_QTY,
				ReplenishImportClassifier.classify(vo, null, 0).resultType());
		assertEquals(SharedCartImportRow.RESULT_INVALID_QTY,
				ReplenishImportClassifier.classify(vo, null, 3).resultType());

		// 低于 MOQ：建议值至少等于 MOQ
		assertEquals(4, ReplenishImportClassifier.classify(vo, null, 2).suggestedQuantity());
		// 非步长整数倍：建议就近**向上**取整，不能让用户改小到低于起订量
		assertEquals(6, ReplenishImportClassifier.classify(vo, null, 5).suggestedQuantity());
	}

	@Test
	@DisplayName("超库存按库存向下取步长给建议；低于MOQ时无可行建议")
	void overStockSuggestion() {
		ReplenishImportMatchVO vo = matched(9, 4, 2, "950ml/瓶");
		var result = ReplenishImportClassifier.classify(vo, null, 20);
		assertEquals(SharedCartImportRow.RESULT_OVER_STOCK, result.resultType());
		assertEquals(8, result.suggestedQuantity());

		ReplenishImportMatchVO tight = matched(3, 4, 2, "950ml/瓶");
		assertNull(ReplenishImportClassifier.classify(tight, null, 20).suggestedQuantity());
	}

	@Test
	@DisplayName("规格文本不一致判规格变更；清单未填规格时不误报")
	void specChangedOnlyWhenBothSidesPresent() {
		ReplenishImportMatchVO vo = matched(100, 1, 1, "550ml×24/箱");
		assertEquals(SharedCartImportRow.RESULT_SPEC_CHANGED,
				ReplenishImportClassifier.classify(vo, "500ml/瓶", 5).resultType());
		// 清单未填规格：无法比较，不能拦
		assertEquals(SharedCartImportRow.RESULT_OK,
				ReplenishImportClassifier.classify(vo, null, 5).resultType());
		// 全角/半角与空白差异不算变更
		assertEquals(SharedCartImportRow.RESULT_OK,
				ReplenishImportClassifier.classify(vo, "550ml × 24/箱", 5).resultType());
	}

	@Test
	@DisplayName("一切正常时判匹配成功")
	void okWhenEverythingFine() {
		ReplenishImportMatchVO vo = matched(100, 1, 1, "950ml/瓶");
		var result = ReplenishImportClassifier.classify(vo, "950ml/瓶", 4);
		assertEquals(SharedCartImportRow.RESULT_OK, result.resultType());
		assertNotNull(result.message());
	}

}
