package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户概览统计 VO
 */
@Data
@Schema(description = "用户概览统计数据")
public class UserOverviewVO {

	@Schema(description = "用户总数")
	private Long totalUserCount;

	@Schema(description = "今日新增用户数")
	private Long todayNewUserCount;

	@Schema(description = "昨日新增用户数")
	private Long yesterdayNewUserCount;

	@Schema(description = "本月新增用户数")
	private Long monthNewUserCount;

	@Schema(description = "近7日新增用户数")
	private Long sevenDayNewUserCount;

	@Schema(description = "近30日新增用户数")
	private Long thirtyDayNewUserCount;

}
