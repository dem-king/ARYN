package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 补给单计划量/已采量编辑 DTO（确认人操作）。
 *
 * <p>与 {@link SharedCartItemDTO} 的分工：后者是**成员**维护自己的需求，
 * 受"只能改自己的明细"约束；本 DTO 供**确认人/发起人**统一排计划与回填
 * 采购进度，因此可以操作任意成员的明细行。
 *
 * <p>两个数量都可为 null，语义不同：
 * <ul>
 *   <li>{@code plannedQuantity = null} 表示"取消计划"，该行退出进度统计
 *       （不是把计划设成 0）；</li>
 *   <li>{@code fulfilledQuantity = null} 表示"本次不改已采量"，
 *       便于只调计划的场景。</li>
 * </ul>
 *
 * @author aryn
 * @since 2026/9/22
 */
@Data
@Schema(description = "补给单计划量/已采量编辑DTO")
public class SharedCartPlanDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "明细ID")
	@NotBlank(message = "明细ID不能为空")
	private String itemId;

	@Schema(description = "计划采购量（采购单位）；传 null 表示取消计划")
	@Min(value = 0, message = "计划量不能为负数")
	@Max(value = 999999, message = "计划量过大")
	private Integer plannedQuantity;

	@Schema(description = "已采量（采购单位）；传 null 表示不修改")
	@Min(value = 0, message = "已采量不能为负数")
	@Max(value = 999999, message = "已采量过大")
	private Integer fulfilledQuantity;

	@Schema(description = "是否显式清除计划量（plannedQuantity 为 null 时区分「不改」与「取消计划」）")
	private Boolean clearPlanned;

}
