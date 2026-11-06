package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 销售离职交接请求
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "销售离职交接请求")
public class SalesStaffResignDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "离职销售ID")
	@NotBlank(message = "请选择离职销售")
	private String staffId;

	@Schema(description = "客户接收销售ID（必填）")
	@NotBlank(message = "请选择客户接收人")
	private String receiveStaffId;

	@Schema(description = "离职时间，默认当前时间")
	private LocalDateTime leaveTime;

	@Schema(description = "交接原因")
	private String reason;

}
