package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 拣货波次（同港口、相近时间窗订单合并作业）。
 *
 * <p>状态机：1待拣货 2拣货中 3已复核 4已交司机 5已完成 6已取消。
 * 交接司机后仓库不能再修改已交接明细。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fulfillment_wave")
public class FulfillmentWave extends Model<FulfillmentWave> {

	public static final String STATUS_PENDING_PICK = "1";

	public static final String STATUS_PICKING = "2";

	public static final String STATUS_REVIEWED = "3";

	public static final String STATUS_HANDED_OVER = "4";

	public static final String STATUS_COMPLETED = "5";

	public static final String STATUS_CANCELED = "6";

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 波次编号 */
	private String waveNo;

	/** 仓库ID */
	private String warehouseId;

	/** 目的港口编码 */
	private String portCode;

	/** 目的港口名称快照 */
	private String portName;

	/** 关联靠港计划ID */
	private String vesselCallId;

	/** 状态：1待拣货 2拣货中 3已复核 4已交司机 5已完成 6已取消 */
	private String status;

	/** 计划交付时间 */
	private LocalDateTime planDeliveryTime;

	/** 拣货完成时间 */
	private LocalDateTime pickedTime;

	/** 复核完成时间 */
	private LocalDateTime reviewedTime;

	/** 交接司机时间 */
	private LocalDateTime handedOverTime;

	/** 波次完成时间 */
	private LocalDateTime completedTime;

	/** 仓库操作员ID */
	private String operatorId;

	/** 仓库操作员姓名快照 */
	private String operatorName;

	/** 备注 */
	private String remark;

	/** 乐观锁版本号 */
	private Integer version;

	private String tenantId;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

}
