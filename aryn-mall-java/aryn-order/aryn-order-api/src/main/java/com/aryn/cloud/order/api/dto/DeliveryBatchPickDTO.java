
package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 批量确认/取消取货
 *
 * @author aryn
 * @since 2026/10/10
 */
@Data
@Schema(description = "批量确认取货")
public class DeliveryBatchPickDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "取货明细ID列表")
	@NotEmpty(message = "取货明细ID列表不能为空")
	private List<String> itemIds;

	@Schema(description = "true 确认取货，false 取消确认")
	private boolean picked = true;

}
