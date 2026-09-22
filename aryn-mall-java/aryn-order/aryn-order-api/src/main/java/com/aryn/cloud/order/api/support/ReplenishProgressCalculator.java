package com.aryn.cloud.order.api.support;

import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.vo.ReplenishProgressVO;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;

/**
 * 补给单进度计算（纯函数，便于单测）。
 *
 * <p>把口径集中在一处：列表行、首页卡片、详情 sheet 都用它，
 * 避免三个地方各写一套除法后出现"首页 65% / 详情 62%"这种自相矛盾。
 *
 * <p>**未设计划的行一律不参与进度**：既不按需求量凑数，也不计成 0%，
 * 而是单独归入 {@code unplannedItems}，让前端如实展示"还有 N 项未设计划"。
 *
 * @author aryn
 * @since 2026/9/22
 */
public final class ReplenishProgressCalculator {

	private ReplenishProgressCalculator() {
	}

	/**
	 * 计算单行进度。
	 *
	 * @param plannedQuantity   计划量，null 表示未设计划
	 * @param fulfilledQuantity 已采量，null 视为 0
	 * @return 进度；未设计划时 remaining/completed 为 null，由前端显示"未设计划"
	 */
	public static ReplenishProgressVO ofRow(Integer plannedQuantity, Integer fulfilledQuantity) {
		ReplenishProgressVO vo = new ReplenishProgressVO();
		vo.setPlannedQuantity(plannedQuantity);
		int fulfilled = fulfilledQuantity == null ? 0 : Math.max(fulfilledQuantity, 0);
		vo.setFulfilledQuantity(fulfilled);
		if (plannedQuantity == null) {
			// 未设计划：不编造进度，两个判断字段留空
			vo.setRemainingQuantity(null);
			vo.setCompleted(null);
			return vo;
		}
		int planned = Math.max(plannedQuantity, 0);
		// 超采不显示负数
		vo.setRemainingQuantity(Math.max(planned - fulfilled, 0));
		vo.setCompleted(fulfilled >= planned);
		return vo;
	}

	/**
	 * 汇总整单进度。
	 *
	 * <p>按**项数**汇总而非数量：不同商品计量单位不同（瓶/箱/kg），
	 * 把数量相加得到的分母没有业务含义。
	 *
	 * @param items        有效明细（调用方保证已排除已移除行）
	 * @param amountPerSku 各明细的估算金额；为 null 或返回 null 时不计入合计
	 */
	public static ReplenishProgressVO.Summary summarize(List<SharedCartItem> items,
			Function<SharedCartItem, BigDecimal> amountPerSku) {
		ReplenishProgressVO.Summary summary = new ReplenishProgressVO.Summary();
		if (items == null || items.isEmpty()) {
			return summary;
		}
		summary.setTotalItems(items.size());

		int plannedItems = 0;
		int fulfilledItems = 0;
		int unplannedItems = 0;
		BigDecimal total = BigDecimal.ZERO;

		for (SharedCartItem item : items) {
			ReplenishProgressVO row = ofRow(item.getPlannedQuantity(), item.getFulfilledQuantity());
			if (row.getPlannedQuantity() == null) {
				unplannedItems++;
			}
			else {
				plannedItems++;
				if (Boolean.TRUE.equals(row.getCompleted())) {
					fulfilledItems++;
				}
			}
			if (amountPerSku != null) {
				BigDecimal amount = amountPerSku.apply(item);
				if (amount != null) {
					total = total.add(amount);
				}
			}
		}

		summary.setPlannedItems(plannedItems);
		summary.setFulfilledItems(fulfilledItems);
		summary.setRemainingItems(plannedItems - fulfilledItems);
		summary.setUnplannedItems(unplannedItems);
		summary.setTotalAmount(total);
		// 无任何计划时百分比为 null（前端显示"—"），而不是 0% —— 0% 会让人
		// 误以为"有计划但一项没采"，与"根本没排计划"是两回事。
		summary.setProgressPercent(plannedItems == 0
				? null
				: (int) Math.round(fulfilledItems * 100.0 / plannedItems));
		return summary;
	}

}
