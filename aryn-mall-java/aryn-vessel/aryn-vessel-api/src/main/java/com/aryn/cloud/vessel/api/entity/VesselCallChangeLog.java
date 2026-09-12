package com.aryn.cloud.vessel.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 靠港计划变更日志（ETA/ETD/泊位/时间窗变更留痕，触发影响面提醒）。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vessel_call_change_log")
public class VesselCallChangeLog extends Model<VesselCallChangeLog> {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 靠港计划ID */
	private String callId;

	/** 船舶ID */
	private String vesselId;

	/** 原 ETA */
	private LocalDateTime oldEta;

	/** 新 ETA */
	private LocalDateTime newEta;

	/** 原 ETD */
	private LocalDateTime oldEtd;

	/** 新 ETD */
	private LocalDateTime newEtd;

	/** 原泊位 */
	private String oldBerth;

	/** 新泊位 */
	private String newBerth;

	/** 原时间窗开始 */
	private LocalDateTime oldWindowStart;

	/** 新时间窗开始 */
	private LocalDateTime newWindowStart;

	/** 原时间窗结束 */
	private LocalDateTime oldWindowEnd;

	/** 新时间窗结束 */
	private LocalDateTime newWindowEnd;

	/** 操作人ID */
	private String operatorId;

	/** 操作人姓名快照 */
	private String operatorName;

	/** 变更说明 */
	private String remark;

	private String tenantId;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

}
