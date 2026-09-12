package com.aryn.cloud.vessel.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 靠港计划变更通知（RocketMQ 广播事件）。
 *
 * <p>人工修改计划中靠港的 ETA/ETD/泊位/时间窗后由船舶域发布；
 * 订单域消费并计算影响面（未完成订单、收集中/待确认共享购物车），
 * 站内信提醒相关用户。快照原则：已支付订单的配送上下文快照不自动改写。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
public class VesselCallChangedNotice implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String changeLogId;

	private String tenantId;

	private String callId;

	private String vesselId;

	private String vesselName;

	private String portCode;

	private String portName;

	private LocalDateTime oldEta;

	private LocalDateTime newEta;

	private LocalDateTime oldEtd;

	private LocalDateTime newEtd;

	private String oldBerth;

	private String newBerth;

	private LocalDateTime oldWindowStart;

	private LocalDateTime newWindowStart;

	private LocalDateTime oldWindowEnd;

	private LocalDateTime newWindowEnd;

	private String operatorName;

}
