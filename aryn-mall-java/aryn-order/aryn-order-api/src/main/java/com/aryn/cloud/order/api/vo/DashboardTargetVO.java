package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 管理端概览月度目标与实际达成。
 */
@Data
@Schema(description = "管理端概览月度目标与实际达成")
public class DashboardTargetVO {

	@Schema(description = "目标月份 yyyy-MM")
	private String targetMonth;

	@Schema(description = "GMV 目标")
	private BigDecimal gmvTarget;

	@Schema(description = "支付订单目标")
	private Integer orderTarget;

	@Schema(description = "新增用户目标")
	private Integer newUserTarget;

	@Schema(description = "本月至今 GMV")
	private BigDecimal gmvActual;

	@Schema(description = "本月至今支付订单数")
	private Integer orderActual;

	@Schema(description = "本月至今新增用户数")
	private Integer newUserActual;

}
