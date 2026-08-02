
package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 配送进度时间线
 *
 * @author aryn
 * @since 2025/7/31
 */
@Data
public class DeliveryProgressVO {

	@Schema(description = "任务ID")
	private String taskId;

	@Schema(description = "任务编号")
	private String taskNo;

	@Schema(description = "出车单号")
	private String tripNo;

	@Schema(description = "配送员姓名")
	private String staffName;

	@Schema(description = "配送员手机号")
	private String staffPhone;

	@Schema(description = "当前状态")
	private String status;

	@Schema(description = "当前状态描述")
	private String statusDesc;

	@Schema(description = "派单时间")
	private LocalDateTime assignTime;

	@Schema(description = "取货开始时间")
	private LocalDateTime pickUpTime;

	@Schema(description = "出发配送时间")
	private LocalDateTime departTime;

	@Schema(description = "送达时间")
	private LocalDateTime arriveTime;

	@Schema(description = "签收时间")
	private LocalDateTime signTime;

	@Schema(description = "收货人姓名")
	private String recipientName;

	@Schema(description = "收货人电话")
	private String recipientPhone;

	@Schema(description = "收货完整地址")
	private String recipientAddress;

	@Schema(description = "仓库地址")
	private String warehouseAddress;

}