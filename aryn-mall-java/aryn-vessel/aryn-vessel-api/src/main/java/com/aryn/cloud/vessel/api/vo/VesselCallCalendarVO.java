package com.aryn.cloud.vessel.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 靠港日历条目 VO。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@Schema(description = "靠港日历条目VO")
public class VesselCallCalendarVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "靠港计划ID")
	private String callId;

	@Schema(description = "船舶ID")
	private String vesselId;

	@Schema(description = "船舶名称")
	private String vesselName;

	@Schema(description = "港口编码")
	private String portCode;

	@Schema(description = "港口名称")
	private String portName;

	@Schema(description = "泊位")
	private String berth;

	@Schema(description = "ETA")
	private LocalDateTime eta;

	@Schema(description = "ETD")
	private LocalDateTime etd;

	@Schema(description = "配送时间窗开始")
	private LocalDateTime deliveryWindowStart;

	@Schema(description = "配送时间窗结束")
	private LocalDateTime deliveryWindowEnd;

	@Schema(description = "靠港状态：1计划中 2靠泊中 3已完成 4已取消")
	private String status;

}
