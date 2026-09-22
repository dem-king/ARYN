package com.aryn.cloud.order.api.support;

import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.vo.ReplenishProgressVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 补给单进度口径测试。
 *
 * <p>重点不是除法，而是三条业务约束：
 * ① 未设计划的行不得参与进度（否则就是假数据）；
 * ② 超采不得显示负数；
 * ③ 整单按项数而非数量汇总（各单位不可相加）。
 */
class ReplenishProgressCalculatorTest {

	private SharedCartItem item(Integer planned, Integer fulfilled) {
		SharedCartItem row = new SharedCartItem();
		row.setPlannedQuantity(planned);
		row.setFulfilledQuantity(fulfilled);
		return row;
	}

	@Test
	@DisplayName("正常进度：目标 4 已采 4 -> 还差 0 且已完成")
	void completedRow() {
		ReplenishProgressVO vo = ReplenishProgressCalculator.ofRow(4, 4);

		assertEquals(0, vo.getRemainingQuantity());
		assertTrue(vo.getCompleted());
	}

	@Test
	@DisplayName("部分完成：目标 12 已采 8 -> 还差 4 且未完成")
	void partialRow() {
		ReplenishProgressVO vo = ReplenishProgressCalculator.ofRow(12, 8);

		assertEquals(4, vo.getRemainingQuantity());
		assertFalse(vo.getCompleted());
	}

	@Test
	@DisplayName("超采：目标 5 已采 7 -> 还差按 0 计，不出现负数")
	void overFulfilledNeverNegative() {
		ReplenishProgressVO vo = ReplenishProgressCalculator.ofRow(5, 7);

		assertEquals(0, vo.getRemainingQuantity());
		assertTrue(vo.getCompleted());
	}

	@Test
	@DisplayName("未设计划：remaining/completed 为 null，不得按需求量凑数")
	void unplannedRowDoesNotFabricate() {
		ReplenishProgressVO vo = ReplenishProgressCalculator.ofRow(null, 3);

		assertNull(vo.getPlannedQuantity());
		assertNull(vo.getRemainingQuantity());
		assertNull(vo.getCompleted());
		assertEquals(3, vo.getFulfilledQuantity());
	}

	@Test
	@DisplayName("已采量为 null 视为 0")
	void nullFulfilledTreatedAsZero() {
		ReplenishProgressVO vo = ReplenishProgressCalculator.ofRow(3, null);

		assertEquals(0, vo.getFulfilledQuantity());
		assertEquals(3, vo.getRemainingQuantity());
		assertFalse(vo.getCompleted());
	}

	@Test
	@DisplayName("空明细：汇总全 0 且百分比为 null")
	void emptySummary() {
		ReplenishProgressVO.Summary summary = ReplenishProgressCalculator.summarize(List.of(), null);

		assertEquals(0, summary.getTotalItems());
		assertEquals(0, summary.getPlannedItems());
		assertNull(summary.getProgressPercent());
		assertEquals(0, BigDecimal.ZERO.compareTo(summary.getTotalAmount()));
	}

	@Test
	@DisplayName("整单按项数汇总：1 项采满 / 3 项有计划 = 33%")
	void summaryCountsItemsNotQuantities() {
		// 数量刻意悬殊：若按数量汇总会算出完全不同的百分比
		List<SharedCartItem> items = List.of(
				item(1, 1),      // 采满
				item(1000, 0),   // 未采
				item(2, 0));     // 未采

		ReplenishProgressVO.Summary summary = ReplenishProgressCalculator.summarize(items, null);

		assertEquals(3, summary.getTotalItems());
		assertEquals(3, summary.getPlannedItems());
		assertEquals(1, summary.getFulfilledItems());
		assertEquals(2, summary.getRemainingItems());
		// 按数量会是 1/1003 ≈ 0%，按项数才是 1/3 ≈ 33%
		assertEquals(33, summary.getProgressPercent());
	}

	@Test
	@DisplayName("未设计划的项单独归类，不拉低百分比")
	void unplannedItemsExcludedFromPercent() {
		List<SharedCartItem> items = List.of(
				item(2, 2),      // 有计划且采满
				item(null, 5));  // 未设计划

		ReplenishProgressVO.Summary summary = ReplenishProgressCalculator.summarize(items, null);

		assertEquals(2, summary.getTotalItems());
		assertEquals(1, summary.getPlannedItems());
		assertEquals(1, summary.getUnplannedItems());
		// 未设计划不应把百分比拉成 50%
		assertEquals(100, summary.getProgressPercent());
	}

	@Test
	@DisplayName("全部未设计划：百分比为 null，与「有计划但没采」区分开")
	void allUnplannedGivesNullPercent() {
		List<SharedCartItem> summaryItems = List.of(item(null, 0), item(null, 3));

		ReplenishProgressVO.Summary summary = ReplenishProgressCalculator.summarize(summaryItems, null);

		assertEquals(0, summary.getPlannedItems());
		assertEquals(2, summary.getUnplannedItems());
		assertNull(summary.getProgressPercent());
	}

	@Test
	@DisplayName("有计划但一项没采：百分比为 0（不是 null）")
	void plannedButNoneFulfilledIsZero() {
		ReplenishProgressVO.Summary summary = ReplenishProgressCalculator.summarize(List.of(item(5, 0)), null);

		assertEquals(1, summary.getPlannedItems());
		assertEquals(0, summary.getProgressPercent());
	}

	@Test
	@DisplayName("合计金额按回调累加，null 金额跳过")
	void sumsAmountsSkippingNull() {
		List<SharedCartItem> items = List.of(item(2, 0), item(3, 0), item(1, 0));

		ReplenishProgressVO.Summary summary = ReplenishProgressCalculator.summarize(items, row -> {
			if (row.getPlannedQuantity() == 1) {
				return null; // 模拟商品域查不到售价
			}
			return new BigDecimal("10.50");
		});

		assertEquals(0, new BigDecimal("21.00").compareTo(summary.getTotalAmount()));
	}

}
