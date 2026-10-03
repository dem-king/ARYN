package com.aryn.cloud.order.support;

import com.aryn.cloud.order.api.entity.SharedCartImportRow;
import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 补给单导入报告分类口径测试。
 *
 * <p>这些断言同时守三类缺陷：
 * <ul>
 *   <li>把「已下架」误判成「未匹配」——用户会一直补选不到，且看不出原因；</li>
 *   <li>把「数量异常」与「超库存」混为一谈——报告的动作会指错（该调数量却让改规格）；</li>
 *   <li>把「没填数量」当成「数量异常」——目录模板铺满在售商品，
 *       客户只填几行，报告会被几百条红色报错淹没。</li>
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
		var result = ReplenishImportClassifier.classify(null, "950ml", "3");
		assertEquals(SharedCartImportRow.RESULT_UNMATCHED, result.resultType());
	}

	@Test
	@DisplayName("数量列为空判「未填数量」，不是数量异常：目录模板里这是正常状态")
	void blankQuantityIsNotFilled() {
		ReplenishImportMatchVO vo = matched(10, 1, 1, "950ml/瓶");
		for (String blank : new String[] { null, "", "   " }) {
			var result = ReplenishImportClassifier.classify(vo, null, blank);
			assertEquals(SharedCartImportRow.RESULT_NOT_FILLED, result.resultType(),
					"空数量必须单独成类，否则目录模板会刷屏报错");
			assertNull(result.suggestedQuantity(), "不采购的行不该给建议数量");
		}
	}

	@Test
	@DisplayName("未填数量的判定先于未匹配与下架：没打算买就不该报错")
	void notFilledWinsOverOtherProblems() {
		// 商品已下架、编码也没匹配上，但客户根本没填数量 —— 这行与客户无关
		assertEquals(SharedCartImportRow.RESULT_NOT_FILLED,
				ReplenishImportClassifier.classify(null, null, "").resultType());

		ReplenishImportMatchVO offShelf = matched(10, 1, 1, "950ml/瓶");
		offShelf.setSkuStatus("1");
		assertEquals(SharedCartImportRow.RESULT_NOT_FILLED,
				ReplenishImportClassifier.classify(offShelf, null, null).resultType());
	}

	@Test
	@DisplayName("填了但解析不出数字才算数量异常，与「没填」区分开")
	void filledButUnparsableIsInvalid() {
		ReplenishImportMatchVO vo = matched(100, 1, 1, "950ml/瓶");
		assertEquals(SharedCartImportRow.RESULT_INVALID_QTY,
				ReplenishImportClassifier.classify(vo, null, "十份").resultType());
		assertEquals(SharedCartImportRow.RESULT_INVALID_QTY,
				ReplenishImportClassifier.classify(vo, null, "0").resultType());
	}

	@Test
	@DisplayName("多规格歧义进未匹配并提示人工确认，不自动入单")
	void ambiguousIsUnmatched() {
		ReplenishImportMatchVO vo = matched(10, 1, 1, "950ml/瓶");
		vo.setMatchType("AMBIGUOUS");
		vo.setSkuId(null);
		var result = ReplenishImportClassifier.classify(vo, null, "3");
		assertEquals(SharedCartImportRow.RESULT_UNMATCHED, result.resultType());
	}

	@Test
	@DisplayName("SKU 或 SPU 下架单独成类，不与未匹配混算")
	void offShelfIsSeparate() {
		ReplenishImportMatchVO skuOff = matched(10, 1, 1, "950ml/瓶");
		skuOff.setSkuStatus("1");
		assertEquals(SharedCartImportRow.RESULT_OFF_SHELF,
				ReplenishImportClassifier.classify(skuOff, null, "3").resultType());

		ReplenishImportMatchVO spuOff = matched(10, 1, 1, "950ml/瓶");
		spuOff.setSpuStatus("0");
		assertEquals(SharedCartImportRow.RESULT_OFF_SHELF,
				ReplenishImportClassifier.classify(spuOff, null, "3").resultType());
	}

	@Test
	@DisplayName("低于MOQ/非步长整数倍都进数量异常，并给出建议值")
	void invalidQuantity() {
		ReplenishImportMatchVO vo = matched(100, 4, 2, "950ml/瓶");
		assertEquals(SharedCartImportRow.RESULT_INVALID_QTY,
				ReplenishImportClassifier.classify(vo, null, "3").resultType());

		// 低于 MOQ：建议值至少等于 MOQ
		assertEquals(4, ReplenishImportClassifier.classify(vo, null, "2").suggestedQuantity());
		// 非步长整数倍：建议就近**向上**取整，不能让用户改小到低于起订量
		assertEquals(6, ReplenishImportClassifier.classify(vo, null, "5").suggestedQuantity());
	}

	@Test
	@DisplayName("数量异常的建议值必须同时受库存约束，不能给出超库存的建议")
	void invalidQuantitySuggestionRespectsStock() {
		// 真实缺陷复现：数量 99999、库存 3000、moq=20、step=10。
		// 仅按步长向上取整会建议 100000 —— 用户点「调整为建议数量」后
		// 依然会因超库存被跳过，动作指向一个不可行的值。
		ReplenishImportMatchVO vo = matched(3000, 20, 10, "16mm");
		var result = ReplenishImportClassifier.classify(vo, null, "99999");
		assertEquals(SharedCartImportRow.RESULT_INVALID_QTY, result.resultType());
		assertEquals(3000, result.suggestedQuantity(), "建议值必须落在库存内");
		assertTrue(result.suggestedQuantity() <= 3000);
	}

	@Test
	@DisplayName("数量异常且库存不足起订量时不给建议值，提示人工补选")
	void invalidQuantitySuggestionNullWhenStockTooLow() {
		ReplenishImportMatchVO vo = matched(5, 20, 10, "16mm");
		var result = ReplenishImportClassifier.classify(vo, null, "99999");
		assertEquals(SharedCartImportRow.RESULT_INVALID_QTY, result.resultType());
		assertNull(result.suggestedQuantity());
	}

	@Test
	@DisplayName("数量异常但库存充足时，仍按步长向上取整给建议")
	void invalidQuantitySuggestionWhenStockPlenty() {
		ReplenishImportMatchVO vo = matched(100000, 20, 10, "16mm");
		var result = ReplenishImportClassifier.classify(vo, null, "99999");
		assertEquals(SharedCartImportRow.RESULT_INVALID_QTY, result.resultType());
		assertEquals(100000, result.suggestedQuantity());
	}

	@Test
	@DisplayName("超库存按库存向下取步长给建议；低于MOQ时无可行建议")
	void overStockSuggestion() {
		ReplenishImportMatchVO vo = matched(9, 4, 2, "950ml/瓶");
		var result = ReplenishImportClassifier.classify(vo, null, "20");
		assertEquals(SharedCartImportRow.RESULT_OVER_STOCK, result.resultType());
		assertEquals(8, result.suggestedQuantity());

		ReplenishImportMatchVO tight = matched(3, 4, 2, "950ml/瓶");
		assertNull(ReplenishImportClassifier.classify(tight, null, "20").suggestedQuantity());
	}

	@Test
	@DisplayName("规格文本不一致判规格变更；清单未填规格时不误报")
	void specChangedOnlyWhenBothSidesPresent() {
		ReplenishImportMatchVO vo = matched(100, 1, 1, "550ml×24/箱");
		assertEquals(SharedCartImportRow.RESULT_SPEC_CHANGED,
				ReplenishImportClassifier.classify(vo, "500ml/瓶", "5").resultType());
		// 清单未填规格：无法比较，不能拦
		assertEquals(SharedCartImportRow.RESULT_OK,
				ReplenishImportClassifier.classify(vo, null, "5").resultType());
		// 全角/半角与空白差异不算变更
		assertEquals(SharedCartImportRow.RESULT_OK,
				ReplenishImportClassifier.classify(vo, "550ml × 24/箱", "5").resultType());
	}

	@Test
	@DisplayName("一切正常时判匹配成功")
	void okWhenEverythingFine() {
		ReplenishImportMatchVO vo = matched(100, 1, 1, "950ml/瓶");
		var result = ReplenishImportClassifier.classify(vo, "950ml/瓶", "4");
		assertEquals(SharedCartImportRow.RESULT_OK, result.resultType());
		assertNotNull(result.message());
	}

}
