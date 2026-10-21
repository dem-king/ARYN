package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 用户侧概览聚合数据。
 */
@Data
@Schema(description = "用户侧概览聚合数据")
public class UserDashboardStatisticsVO {

	@Schema(description = "用户概览")
	private UserOverviewVO overview;

	@Schema(description = "流量来源分布")
	private List<UserStatisticsVO> sourceStatistics;

}
