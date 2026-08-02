
package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 送货顺序调整
 *
 * @author aryn
 * @since 2025/7/31
 */
@Data
public class DeliverySortDTO {

	@Schema(description = "任务ID按顺序排列")
	private List<String> taskIds;

}