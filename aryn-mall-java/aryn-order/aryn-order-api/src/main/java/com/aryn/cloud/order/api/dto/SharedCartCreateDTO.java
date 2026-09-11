package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 创建共享购物车 DTO。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@Schema(description = "创建共享购物车DTO")
public class SharedCartCreateDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "船舶ID")
	@NotBlank(message = "船舶ID不能为空")
	private String vesselId;

	@Schema(description = "靠港计划ID")
	@NotBlank(message = "靠港计划ID不能为空")
	private String vesselCallId;

	@Schema(description = "收集截止时间")
	private LocalDateTime expiresAt;

	@Schema(description = "备注")
	private String remark;

}
