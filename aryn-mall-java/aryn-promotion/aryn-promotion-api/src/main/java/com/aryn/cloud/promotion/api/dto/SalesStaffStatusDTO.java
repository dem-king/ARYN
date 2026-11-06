package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 销售员状态变更请求
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "销售员状态变更请求")
public class SalesStaffStatusDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "销售ID")
	@NotBlank(message = "销售ID不能为空")
	private String id;

	@Schema(description = "状态：0在职 1离职")
	@NotBlank(message = "状态不能为空")
	private String status;

}
