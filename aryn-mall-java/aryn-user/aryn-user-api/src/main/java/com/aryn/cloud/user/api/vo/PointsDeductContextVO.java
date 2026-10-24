package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 积分抵现上下文：下单/结算时由用户域返回，订单域据此计算可抵扣额度。
 *
 * <p>除可用积分与汇率外，还携带租户级积分风控配置（抵扣上限比例/最低使用门槛/单笔封顶）
 * 与积分排除清单（类目/商品黑名单），订单域按明细归属类目/商品过滤可抵扣基数。
 */
@Data
@Schema(description = "积分抵现上下文")
public class PointsDeductContextVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "是否启用积分抵现")
	private boolean enabled;

	@Schema(description = "当前可用积分")
	private Integer availablePoints;

	@Schema(description = "每抵扣 1 元所需积分")
	private Integer pointsPerYuan;

	@Schema(description = "单笔抵扣上限比例（百分数，如 20.00 表示 20%），空=按默认 20%")
	private BigDecimal deductMaxRatio;

	@Schema(description = "最低使用门槛（积分可抵基数满 X 元可用，0 或空=无门槛）")
	private BigDecimal minUseAmount;

	@Schema(description = "单笔抵扣金额封顶（元，0 或空=不封顶）")
	private BigDecimal maxDeductAmount;

	@Schema(description = "排除积分的类目 ID 集合（命中即不参与积分抵扣与返积分）")
	private List<String> excludeCategoryIds;

	@Schema(description = "排除积分的商品（SPU）ID 集合")
	private List<String> excludeSpuIds;

}
