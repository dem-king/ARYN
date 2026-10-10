
package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 司机工作台的订单摘要行：一趟车上的一站。
 *
 * <p>只带「司机在列表上做判断」需要的字段（收货人、目的地要素、状态、顺序、时间窗），
 * 不取取货明细 —— 工作台首屏要装下整趟车，明细留给出车单详情页。
 *
 * @author aryn
 * @since 2026/10/10
 */
@Data
@Schema(description = "司机工作台订单摘要")
public class DeliveryTripTaskBriefVO {

	@Schema(description = "配送任务ID")
	private String id;

	@Schema(description = "任务编号")
	private String taskNo;

	@Schema(description = "订单号")
	private String orderNo;

	@Schema(description = "送货顺序（趟车内序号，1 起）")
	private Integer sortNo;

	@Schema(description = "状态：2待取货 3配货中 4待送达 5已送达 6已签收 7已取消 8异常 9待退回")
	private String status;

	@Schema(description = "收货人姓名")
	private String recipientName;

	@Schema(description = "收货人电话")
	private String recipientPhone;

	@Schema(description = "收货完整地址（船供单可能为空，前端回落港口+泊位）")
	private String recipientAddress;

	@Schema(description = "配送船舶名称快照")
	private String vesselName;

	@Schema(description = "港口名称快照")
	private String portName;

	@Schema(description = "泊位快照")
	private String berth;

	@Schema(description = "配送时间窗开始")
	private LocalDateTime deliveryWindowStart;

	@Schema(description = "配送时间窗结束")
	private LocalDateTime deliveryWindowEnd;

	@Schema(description = "送达时间")
	private LocalDateTime arriveTime;

}
