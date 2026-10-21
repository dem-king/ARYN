package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理端概览月度目标保存参数。
 */
@Data
@Schema(description = "管理端概览月度目标保存参数")
public class DashboardTargetDTO {

	@Schema(description = "目标月份 yyyy-MM，留空取当前月")
	private String targetMonth;

	@Schema(description = "GMV 目标")
	private BigDecimal gmvTarget;

	@Schema(description = "支付订单目标")
	private Integer orderTarget;

	@Schema(description = "新增用户目标")
	private Integer newUserTarget;

}
