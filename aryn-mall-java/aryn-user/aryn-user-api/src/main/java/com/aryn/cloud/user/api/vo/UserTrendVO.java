package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户趋势统计 VO
 */
@Data
@Schema(description = "用户趋势统计数据")
public class UserTrendVO {

	@Schema(description = "时间点 (小时 或 日期)")
	private String timePoint;

	@Schema(description = "新增用户数量")
	private Integer newUserCount;

}
