package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 补给单进度（按明细行或整单汇总）。
 *
 * <p>口径定义（唯一定义处，前端与后续查询都以此为准）：
 * <ul>
 *   <li>只有设置了 {@code plannedQuantity} 的行才参与进度统计 ——
 *       未设计划的行不做进度计算，也不回落成需求量，避免造假进度；</li>
 *   <li>{@code remainingQuantity = max(planned - fulfilled, 0)}，
 *       已采超计划时按 0 计（超采不显示负数）；</li>
 *   <li>{@code completed = fulfilled >= planned}；</li>
 *   <li>整单进度按**项数**而非数量汇总 —— 不同商品计量单位不同
 *       （瓶/箱/kg），把数量相加没有意义。</li>
 * </ul>
 *
 * @author aryn
 * @since 2026/9/22
 */
@Data
@Schema(description = "补给单进度")
public class ReplenishProgressVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "计划采购量（采购单位）；null 表示未设计划")
	private Integer plannedQuantity;

	@Schema(description = "已采量（采购单位）")
	private Integer fulfilledQuantity;

	@Schema(description = "还差数量（已采超计划时按 0 计）")
	private Integer remainingQuantity;

	@Schema(description = "本行是否已采满（fulfilled >= planned）")
	private Boolean completed;

	/**
	 * 整单汇总。
	 */
	@Data
	@Schema(description = "补给单整单进度汇总")
	public static class Summary implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "有效明细总项数（不含已移除）")
		private Integer totalItems = 0;

		@Schema(description = "已设计划的项数（只有这些参与进度）")
		private Integer plannedItems = 0;

		@Schema(description = "已采满项数")
		private Integer fulfilledItems = 0;

		@Schema(description = "还差项数（已设计划但未采满）")
		private Integer remainingItems = 0;

		@Schema(description = "未设计划的项数")
		private Integer unplannedItems = 0;

		@Schema(description = "进度百分比 0~100，按已采满项数 / 已设计划项数计算；无计划时为 null")
		private Integer progressPercent;

		@Schema(description = "合计金额（SKU 当前售价 × 计划量，仅估算）")
		private BigDecimal totalAmount = BigDecimal.ZERO;

	}

}
