package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 券兑换成本汇总
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "券兑换成本汇总")
public class DistributionCouponCostVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "兑换笔数")
	private Long exchangeCount;

	@Schema(description = "消耗佣金合计")
	private BigDecimal commissionAmount;

	@Schema(description = "券面额合计（营销费用口径）")
	private BigDecimal faceAmount;

}
