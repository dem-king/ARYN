
package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配送进度时间线节点
 *
 * @author aryn
 * @since 2026/9/22
 */
@Data
@Schema(description = "配送进度时间线节点")
public class DeliveryProgressNode {

	@Schema(description = "节点状态码（对应 delivery_task.status）")
	private String status;

	@Schema(description = "节点名称")
	private String name;

	@Schema(description = "节点发生时间，未完成时为空")
	private LocalDateTime time;

	@Schema(description = "是否已完成")
	private boolean done;

	@Schema(description = "是否当前所处节点")
	private boolean active;

}
