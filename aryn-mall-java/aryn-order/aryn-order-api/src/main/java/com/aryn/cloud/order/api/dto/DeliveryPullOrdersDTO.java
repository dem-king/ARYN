
package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 司机把候选订单拉进当前趟次
 *
 * @author aryn
 * @since 2026/10/10
 */
@Data
@Schema(description = "拉单并入出车单")
public class DeliveryPullOrdersDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "要拉进本趟的订单ID列表")
	@NotEmpty(message = "请至少选择一个订单")
	private List<String> orderIds;

}
