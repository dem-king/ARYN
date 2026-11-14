package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 管理端采购主体批量审核 / 批量停用 DTO。
 *
 * @author aryn
 * @since 2026/11/13
 */
@Data
@Schema(description = "采购主体批量审核DTO")
public class BuyerOrgBatchAuditDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@NotEmpty(message = "主体ID不能为空")
	@Schema(description = "采购主体ID列表")
	private List<String> ids;

	@NotBlank(message = "审核状态不能为空")
	@Schema(description = "审核结果：1通过 2驳回 3停用")
	private String auditStatus;

	@Schema(description = "审核意见")
	private String auditRemark;

}
