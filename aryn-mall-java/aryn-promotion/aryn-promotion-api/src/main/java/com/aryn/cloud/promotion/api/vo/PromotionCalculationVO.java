package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 营销计算结果 VO。
 *
 * <p>阶梯价以“单价覆盖”表达（改 SKU 成交基价，member/coupon 在其上计算）；
 * 整船优惠以整单金额表达（订单侧按金额占比分摊到明细）。
 * 所有命中的活动带规则快照，订单历史金额不随活动配置变化。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@Schema(description = "营销计算结果VO")
public class PromotionCalculationVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "整单优惠合计（不含阶梯价改价部分）")
	private BigDecimal totalDiscount;

	@Schema(description = "阶梯价单价覆盖（改 SKU 成交基价）")
	private List<UnitPriceOverride> ladderOverrides;

	@Schema(description = "整船/整单优惠金额")
	private BigDecimal wholeDiscount;

	@Schema(description = "命中活动明细（含规则快照）")
	private List<ActivityDetail> details;

	@Data
	@Schema(description = "阶梯价单价覆盖")
	public static class UnitPriceOverride implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "SKU ID")
		private String skuId;

		@Schema(description = "活动后单价")
		private BigDecimal unitPrice;

		@Schema(description = "原结算基价")
		private BigDecimal originalPrice;

		@Schema(description = "命中活动ID")
		private String activityId;

	}

	@Data
	@Schema(description = "命中活动明细")
	public static class ActivityDetail implements Serializable {

		@Serial
		private static final long serialVersionUID = 1L;

		@Schema(description = "活动ID")
		private String activityId;

		@Schema(description = "活动类型")
		private String activityType;

		@Schema(description = "活动名称")
		private String activityName;

		@Schema(description = "本活动优惠金额（整单类）")
		private BigDecimal discountAmount;

		@Schema(description = "规则快照（受控JSON）")
		private String ruleSnapshot;

	}

}
