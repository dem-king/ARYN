package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 创建拣货波次 DTO。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@Schema(description = "创建拣货波次DTO")
public class FulfillmentWaveCreateDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "仓库ID")
	private String warehouseId;

	@Schema(description = "目的港口编码")
	@NotBlank(message = "港口编码不能为空")
	private String portCode;

	@Schema(description = "目的港口名称")
	private String portName;

	@Schema(description = "关联靠港计划ID")
	private String vesselCallId;

	@Schema(description = "计划交付时间")
	private LocalDateTime planDeliveryTime;

	@Schema(description = "备注")
	private String remark;

}
