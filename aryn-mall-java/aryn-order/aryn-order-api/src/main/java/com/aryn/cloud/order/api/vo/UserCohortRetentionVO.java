package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 按首购月份分组的用户留存。
 */
@Data
@Schema(description = "用户首购 Cohort 留存")
public class UserCohortRetentionVO {

	@Schema(description = "首购月份 yyyy-MM")
	private String cohortMonth;

	@Schema(description = "Cohort 用户数")
	private Long cohortSize;

	@Schema(description = "当月留存用户数")
	private Long month0;

	@Schema(description = "第 1 个月留存用户数")
	private Long month1;

	@Schema(description = "第 2 个月留存用户数")
	private Long month2;

	@Schema(description = "第 3 个月留存用户数")
	private Long month3;

	@Schema(description = "第 4 个月留存用户数")
	private Long month4;

	@Schema(description = "第 5 个月留存用户数")
	private Long month5;

}
