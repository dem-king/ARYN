package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
@Schema(description = "页面装修发布申请审批请求")
public class PageDesignReleaseAuditDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@NotNull(message = "审批结果不能为空")
	@Schema(description = "审批结果：true.通过；false.拒绝；")
	private Boolean approved;

	@Schema(description = "审批意见")
	private String auditRemark;

}
