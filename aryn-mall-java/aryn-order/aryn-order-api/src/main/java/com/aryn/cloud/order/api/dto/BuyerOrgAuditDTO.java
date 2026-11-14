package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理端采购主体审核 DTO。
 *
 * @author aryn
 * @since 2026/11/12
 */
@Data
@Schema(description = "采购主体审核DTO")
public class BuyerOrgAuditDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@NotBlank(message = "审核状态不能为空")
	@Schema(description = "审核结果：1通过 2驳回 3停用")
	private String auditStatus;

	@Schema(description = "审核意见")
	private String auditRemark;

}
