package com.aryn.cloud.vessel.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 船舶配送上下文 DTO。
 *
 * <p>C 端结算与下单使用的当前船舶/靠港上下文，字段与订单配送上下文快照对齐。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Data
@Schema(description = "船舶配送上下文DTO")
public class VesselContextDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "船舶ID")
	private String vesselId;

	@Schema(description = "船舶名称")
	private String vesselName;

	@Schema(description = "靠港计划ID")
	private String vesselCallId;

	@Schema(description = "港口编码")
	private String portCode;

	@Schema(description = "港口名称")
	private String portName;

	@Schema(description = "泊位")
	private String berth;

	@Schema(description = "预计到港时间")
	private LocalDateTime eta;

	@Schema(description = "预计离港时间")
	private LocalDateTime etd;

	@Schema(description = "配送时间窗开始")
	private LocalDateTime deliveryWindowStart;

	@Schema(description = "配送时间窗结束")
	private LocalDateTime deliveryWindowEnd;

	/**
	 * 该靠港是否仍可作为新订单的配送计划（未完成/未取消且 ETD 未过）。
	 *
	 * <p>展示用快照查询（{@code getVesselCallSnapshot}）会带着失效靠港一起返回，
	 * 调用方用它区分「这班船已开走」与「上下文还没加载出来」——两者在展示字段上
	 * 都是空值，只看名称无法分辨；订单校验路径取到的上下文恒为 true。
	 */
	@Schema(description = "该靠港是否仍可下单（false=已离港/已完成/已取消，提交时会被顺延）")
	private Boolean callOrderable;

}
