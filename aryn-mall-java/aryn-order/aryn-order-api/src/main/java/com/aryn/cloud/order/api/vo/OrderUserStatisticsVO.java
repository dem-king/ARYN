package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单用户统计 VO
 */
@Data
@Schema(description = "订单用户统计数据")
public class OrderUserStatisticsVO {

	@Schema(description = "成交用户数")
	private Integer payBuyerCount;

	@Schema(description = "复购率 (产生两次及以上购买行为的用户占比)")
	private BigDecimal repurchaseRate;

	@Schema(description = "老客成交占比 (老客户成交金额占总成交额比例)")
	private BigDecimal oldCustomerGmvRate;

	@Schema(description = "老客成交金额")
	private BigDecimal oldCustomerGmv;

	@Schema(description = "总成交金额")
	private BigDecimal totalGmv;

}
