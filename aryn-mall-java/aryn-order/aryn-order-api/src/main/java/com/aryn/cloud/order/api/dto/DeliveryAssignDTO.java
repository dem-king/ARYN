
package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 批量派单
 *
 * @author aryn
 * @since 2025/7/31
 */
@Data
public class DeliveryAssignDTO {

	@Schema(description = "配送任务ID列表")
	@NotEmpty(message = "任务ID列表不能为空")
	private List<String> taskIds;

	@Schema(description = "配送员ID")
	@NotBlank(message = "配送员ID不能为空")
	private String staffId;

}