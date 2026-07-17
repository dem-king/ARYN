package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 提现申请请求
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
@Data
@Schema(description = "提现申请请求")
public class DistributionWithdrawApplyDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@NotNull(message = "提现金额不能为空")
	@Positive(message = "提现金额必须大于0")
	@Schema(description = "提现金额")
	private BigDecimal amount;

	@NotBlank(message = "收款类型不能为空")
	@Schema(description = "收款类型")
	private String accountType;

	@NotBlank(message = "收款人不能为空")
	@Schema(description = "收款人")
	private String accountName;

	@NotBlank(message = "收款账号不能为空")
	@Size(max = 64, message = "收款账号不能超过64个字符")
	@Schema(description = "收款账号")
	private String accountNo;

	@Size(max = 255, message = "备注不能超过255个字符")
	@Schema(description = "备注")
	private String remark;

}
