package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户增长趋势 VO
 */
@Data
@Schema(description = "用户增长趋势数据")
public class UserGrowthTrendVO {

	@Schema(description = "时间点 (小时 或 日期)")
	private String timePoint;

	@Schema(description = "新增用户数量")
	private Integer newUserCount;

	@Schema(description = "成交用户数量 (支付买家数)")
	private Integer transactingUserCount;

}
