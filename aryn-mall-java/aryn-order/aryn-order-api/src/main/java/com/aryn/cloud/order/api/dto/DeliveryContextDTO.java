package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 内部配送上下文 DTO
 *
 * <p>公司港口/船舶内部配送（delivery_way=4）的配送上下文契约：
 * 以船舶、靠港计划、港口、泊位和时间窗为事实来源，
 * 不以用户收货地址为必填条件；地址仅作为普通零售配送的兼容字段保留。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Data
@Schema(description = "内部配送上下文DTO")
public class DeliveryContextDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "船舶ID")
	private String vesselId;

	@Schema(description = "靠港计划ID")
	private String vesselCallId;

	@Schema(description = "港口编码")
	private String portCode;

	@Schema(description = "港口名称")
	private String portName;

	@Schema(description = "泊位")
	private String berth;

	@Schema(description = "配送时间窗开始")
	private LocalDateTime deliveryWindowStart;

	@Schema(description = "配送时间窗结束")
	private LocalDateTime deliveryWindowEnd;

	@Schema(description = "收货人姓名")
	private String receiverName;

	@Schema(description = "收货人电话")
	private String receiverPhone;

	@Schema(description = "船上代理/经办人姓名")
	private String agentName;

	@Schema(description = "船上代理/经办人电话")
	private String agentPhone;

}
