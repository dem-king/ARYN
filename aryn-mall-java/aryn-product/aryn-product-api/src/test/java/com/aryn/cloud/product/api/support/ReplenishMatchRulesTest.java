package com.aryn.cloud.product.api.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

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

	@Test
	@DisplayName("二字组打分：修饰词夹心品名靠核心词重叠对上")
	void bigramScoreSandwichNames() {
		// 接龙写「青香蕉」、商城叫「国产香蕉」：核心词「香蕉」重叠，双向取大 0.5
		assertEquals(0.5, ReplenishMatchRules.bigramScore("青香蕉", "国产香蕉"), 1e-9);
		// 长品名强重叠：「红富士冰糖心」5 组重叠 4 组 = 0.8，够自动命中
		assertEquals(0.8, ReplenishMatchRules.bigramScore("红富士冰糖心", "红富士苹果冰糖心 4斤装"), 1e-9);
		// 候选侧覆盖：「麒麟无籽西瓜」对「麒麟西瓜」，目标 0.4 但候选 0.67
		assertEquals(2.0 / 3.0, ReplenishMatchRules.bigramScore("麒麟无籽西瓜", "麒麟西瓜"), 1e-9);
		// 「小番茄」对「番茄酱」只有 0.5：低于自动阈值，降级为人工候选
		assertEquals(0.5, ReplenishMatchRules.bigramScore("小番茄", "番茄酱"), 1e-9);
	}

	@Test
	@DisplayName("二字组打分：无重叠、单字、空白都是 0 分")
	void bigramScoreNoOverlap() {
		assertEquals(0.0, ReplenishMatchRules.bigramScore("土豆", "西红柿"), 1e-9);
		assertEquals(0.0, ReplenishMatchRules.bigramScore("盐", "盐汽水"), 1e-9);
		assertEquals(0.0, ReplenishMatchRules.bigramScore("", "香蕉"), 1e-9);
		assertEquals(0.0, ReplenishMatchRules.bigramScore("香蕉", null), 1e-9);
	}

	@Test
	@DisplayName("品名匹配变体：剥尾部形容词与规格，名字本身是规格串时不产出空变体")
	void nameVariantsStripAdjectiveAndSpec() {
		assertEquals(List.of("卤牛腱子优质", "卤牛腱子"), ReplenishMatchRules.nameVariants("卤牛腱子优质"));
		assertEquals(List.of("上海盐汽水600ml*24瓶", "上海盐汽水"),
				ReplenishMatchRules.nameVariants("上海盐汽水600ml*24瓶"));
		assertEquals(List.of("青岛啤酒（冰淳）500ML12罐", "青岛啤酒（冰淳）"),
				ReplenishMatchRules.nameVariants("青岛啤酒（冰淳）500ML12罐"));
		assertEquals(List.of("青岛清爽24罐装", "青岛清爽"), ReplenishMatchRules.nameVariants("青岛清爽24罐装"));
		// 剥完不足 2 字符：保留原样，不产出空/残缺变体
		assertEquals(List.of("600ml*24瓶"), ReplenishMatchRules.nameVariants("600ml*24瓶"));
		assertEquals(List.of("橙子"), ReplenishMatchRules.nameVariants("橙子"));
	}

}
