package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 履约异常上报 DTO。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@Schema(description = "履约异常上报DTO")
public class FulfillmentExceptionDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "拣货波次ID")
	private String waveId;

	@Schema(description = "订单ID")
	private String orderId;

	@Schema(description = "配送任务ID")
	private String taskId;

	@Schema(description = "异常类型：SHORT_PICK/WRONG_ITEM/DAMAGE/REPLACE/OTHER")
	@NotBlank(message = "异常类型不能为空")
	private String exceptionType;

	@Schema(description = "异常描述")
	private String description;

	@Schema(description = "证据图片URL（JSON数组）")
	private String evidenceUrls;

}
