package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 发放台账确认发放请求
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "发放台账确认发放请求")
public class SalesPayoutConfirmDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "台账ID")
	@NotBlank(message = "台账ID不能为空")
	private String id;

	@Schema(description = "备注/凭证号")
	private String remark;

}
