
package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 司机工作台的出车单摘要：一张卡片列出这趟车的所有订单（按送货顺序）。
 *
 * <p>件数/单数统计在装配时按任务与明细现算（与出车单详情同口径），
 * 不用 delivery_trip 表上的冗余列，避免派单并入任务后计数漂移。
 *
 * @author aryn
 * @since 2026/10/10
 */
@Data
@Schema(description = "司机工作台出车单摘要")
public class DeliveryTripBriefVO {

	@Schema(description = "出车单ID")
	private String id;

	@Schema(description = "出车单号")
	private String tripNo;

	@Schema(description = "状态：1待配货 2配货中 3配送中 4已完成")
	private String status;

	@Schema(description = "仓库名称")
	private String warehouseName;

	@Schema(description = "仓库地址快照")
	private String warehouseAddress;

	@Schema(description = "任务总数")
	private Integer taskCount;

	@Schema(description = "总条目数（按任务明细汇总）")
	private Integer totalItemCount;

	@Schema(description = "已取条目数")
	private Integer pickedItemCount;

	@Schema(description = "已送达单数")
	private Integer arrivedTaskCount;

	@Schema(description = "装货出发时间")
	private java.time.LocalDateTime departTime;

	@Schema(description = "按送货顺序排列的订单摘要")
	private List<DeliveryTripTaskBriefVO> taskList;

}
