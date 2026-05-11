package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 用户消费分析 VO
 */
@Data
@Schema(description = "用户消费分析数据")
public class UserConsumptionAnalysisVO {

	@Schema(description = "成交用户数")
	private Integer transactingUserCount;

	@Schema(description = "总消费金额")
	private BigDecimal totalConsumptionAmount;

	@Schema(description = "人均消费 (总消费金额 / 成交用户数)")
	private BigDecimal averageConsumptionPerUser;

	@Schema(description = "客单价 (总消费金额 / 总订单数)")
	private BigDecimal averageOrderValue;

	@Schema(description = "总订单数")
	private Integer totalOrderCount;

}
