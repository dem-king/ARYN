package com.aryn.cloud.statistics.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 大屏概览数据 VO
 */
@Data
@Schema(description = "大屏概览数据")
public class VisualOverviewVO {

	@Schema(description = "实时GMV (成交额)")
	private BigDecimal gmv;

	@Schema(description = "订单数")
	private Integer orderCount;

	@Schema(description = "在线用户数")
	private Long onlineUserCount;

	@Schema(description = "转化率 (下单用户/注册用户)")
	private BigDecimal conversionRate;

}