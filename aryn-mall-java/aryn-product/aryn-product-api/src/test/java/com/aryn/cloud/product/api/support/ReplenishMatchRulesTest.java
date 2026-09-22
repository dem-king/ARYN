package com.aryn.cloud.product.api.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 补给单导入匹配共享口径测试（商品域与订单域共用，
 * 口径漂移会直接导致「报告说成功、确认被拦」）。
 */
class ReplenishMatchRulesTest {

	@Test
	@DisplayName("规格规范化：全角括号/乘号、空白、大小写都归一")
	void normalizeSpec() {
		assertEquals(ReplenishMatchRules.normalizeSpec("550ml × 24/箱"),
				ReplenishMatchRules.normalizeSpec("550ml*24/箱"));
		assertEquals(ReplenishMatchRules.normalizeSpec("（冻）10斤"), ReplenishMatchRules.normalizeSpec("(冻)10斤"));
	}

	@Test
	@DisplayName("任一侧规格为空视为无法比较，不报规格变更")
	void specEquivalentWhenBlank() {
		assertTrue(ReplenishMatchRules.specEquivalent(null, "950ml/瓶"));
		assertTrue(ReplenishMatchRules.specEquivalent("950ml/瓶", null));
		assertTrue(ReplenishMatchRules.specEquivalent("", ""));
	}

	@Test
	@DisplayName("规格互相包含视为等价，真实差异才算变更")
	void specEquivalentRealDifference() {
		assertTrue(ReplenishMatchRules.specEquivalent("550ml*24", "550ml*24/箱"));
		assertFalse(ReplenishMatchRules.specEquivalent("500ml", "550ml"));
	}

	@Test
	@DisplayName("数量规则：正整数 + 达到MOQ + 步长整数倍")
	void quantityAcceptable() {
		assertTrue(ReplenishMatchRules.quantityAcceptable(4, 4, 2));
		assertFalse(ReplenishMatchRules.quantityAcceptable(null, 1, 1));
		assertFalse(ReplenishMatchRules.quantityAcceptable(0, 1, 1));
		assertFalse(ReplenishMatchRules.quantityAcceptable(3, 4, 2));
		assertFalse(ReplenishMatchRules.quantityAcceptable(5, 4, 2));
		// MOQ/步长缺省按 1 处理（商品统一后船供资料是可选扩展）
		assertTrue(ReplenishMatchRules.quantityAcceptable(1, null, null));
	}

	@Test
	@DisplayName("超库存建议：库存向下取步长，低于MOQ时无可行建议")
	void suggestedQuantity() {
		assertEquals(8, ReplenishMatchRules.suggestedQuantity(9, 4, 2));
		assertNull(ReplenishMatchRules.suggestedQuantity(3, 4, 2));
		assertNull(ReplenishMatchRules.suggestedQuantity(null, 1, 1));
		assertNull(ReplenishMatchRules.suggestedQuantity(0, 1, 1));
	}

	@Test
	@DisplayName("数量原文解析：容忍 12.0，拒绝小数与非数字")
	void parseQuantity() {
		assertEquals(12, ReplenishMatchRules.parseQuantity("12"));
		assertEquals(12, ReplenishMatchRules.parseQuantity("12.0"));
		assertEquals(12, ReplenishMatchRules.parseQuantity(12));
		assertNull(ReplenishMatchRules.parseQuantity("1.5"));
		assertNull(ReplenishMatchRules.parseQuantity("abc"));
		assertNull(ReplenishMatchRules.parseQuantity(""));
		assertNull(ReplenishMatchRules.parseQuantity(null));
	}

}
