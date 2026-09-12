package com.aryn.cloud.vessel.api.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 靠港计划。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vessel_call")
public class VesselCall extends AbstractVesselEntity {

	/** 船舶ID */
	private String vesselId;

	/** 港口编码 */
	private String portCode;

	/** 港口名称 */
	private String portName;

	/** 泊位 */
	private String berth;

	/** 预计到港时间 */
	private LocalDateTime eta;

	/** 预计离港时间 */
	private LocalDateTime etd;

	/** 配送时间窗开始 */
	private LocalDateTime deliveryWindowStart;

	/** 配送时间窗结束 */
	private LocalDateTime deliveryWindowEnd;

	/** 靠港状态：1计划中 2靠泊中 3已完成 4已取消 */
	private String status;

	/** 操作人ID（管理端修改时透传，不入库） */
	@com.baomidou.mybatisplus.annotation.TableField(exist = false)
	private String operatorId;

	/** 操作人姓名（管理端修改时透传，不入库） */
	@com.baomidou.mybatisplus.annotation.TableField(exist = false)
	private String operatorName;

	/** 备注 */
	private String remark;

}
