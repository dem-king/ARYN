package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 提现审核请求
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
@Data
@Schema(description = "提现审核请求")
public class DistributionWithdrawAuditDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@NotBlank(message = "提现单ID不能为空")
	@Schema(description = "提现单ID")
	private String id;

	@NotBlank(message = "审核状态不能为空")
	@Pattern(regexp = "^[12]$", message = "审核状态只允许1(通过)或2(拒绝)")
	@Schema(description = "审核状态：1通过 2拒绝")
	private String status;

	@Schema(description = "拒绝原因")
	private String rejectReason;

	@Schema(description = "线下打款流水号，审核通过时必填")
	@Size(max = 64, message = "打款流水号不能超过64个字符")
	private String payoutNo;

}
