package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理端申请单审核 DTO。
 *
 * @author aryn
 * @since 2026/11/12
 */
@Data
@Schema(description = "采购主体申请审核DTO")
public class BuyerOrgApplyAuditDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "是否通过：true 通过 / false 驳回")
	private Boolean approved;

	@Schema(description = "审核意见（驳回时必填）")
	private String auditRemark;

	@Schema(description = "审核通过后匹配的主体ID（创建申请为空时由系统落）")
	private String matchedOrgId;

}
