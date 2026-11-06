package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 销售业绩全局配置保存请求
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "销售业绩全局配置保存请求")
public class SalesConfigSaveDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "全局默认提成比例（0-1）")
	@NotNull(message = "默认提成比例不能为空")
	@DecimalMin(value = "0", message = "默认提成比例不能小于0")
	@DecimalMax(value = "1", message = "默认提成比例不能大于1")
	private BigDecimal defaultCommissionRate;

	@Schema(description = "客户转交奖励比例（0-1，0=不启用）")
	@NotNull(message = "转交奖励比例不能为空")
	@DecimalMin(value = "0", message = "转交奖励比例不能小于0")
	@DecimalMax(value = "1", message = "转交奖励比例不能大于1")
	private BigDecimal referralRewardRate;

	@Schema(description = "每月台账生成日（1-28）")
	@NotNull(message = "每月台账生成日不能为空")
	@Min(value = 1, message = "每月台账生成日不能小于1")
	@Max(value = 28, message = "每月台账生成日不能大于28")
	private Integer monthlyCutoffDay;

	@Schema(description = "退款风险观察期（天，0-365）")
	@NotNull(message = "退款风险观察期不能为空")
	@Min(value = 0, message = "退款风险观察期不能小于0")
	@Max(value = 365, message = "退款风险观察期不能大于365")
	private Integer refundRiskDays;

	@Schema(description = "认领有效期（天，1-365）")
	@NotNull(message = "认领有效期不能为空")
	@Min(value = 1, message = "认领有效期不能小于1")
	@Max(value = 365, message = "认领有效期不能大于365")
	private Integer claimExpireDays;

}
