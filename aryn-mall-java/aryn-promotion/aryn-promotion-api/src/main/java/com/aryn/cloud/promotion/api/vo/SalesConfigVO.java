package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 销售业绩全局配置视图
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "销售业绩全局配置视图")
public class SalesConfigVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键（未配置时为空）")
	private String id;

	@Schema(description = "全局默认提成比例（0-1）")
	private BigDecimal defaultCommissionRate;

	@Schema(description = "客户转交奖励比例（0-1，0=不启用）")
	private BigDecimal referralRewardRate;

	@Schema(description = "每月台账生成日（1-28）")
	private Integer monthlyCutoffDay;

	@Schema(description = "退款风险观察期（天）")
	private Integer refundRiskDays;

	@Schema(description = "认领有效期（天）")
	private Integer claimExpireDays;

	@Schema(description = "状态：0启用 1禁用")
	private String status;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "更新时间")
	private LocalDateTime updateTime;

}
