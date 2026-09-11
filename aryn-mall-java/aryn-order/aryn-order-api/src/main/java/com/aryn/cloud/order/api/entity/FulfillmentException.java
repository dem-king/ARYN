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
 * 履约异常（短装/错发/破损/替代等，含证据）。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fulfillment_exception")
public class FulfillmentException extends Model<FulfillmentException> {

	public static final String TYPE_SHORT_PICK = "SHORT_PICK";

	public static final String TYPE_WRONG_ITEM = "WRONG_ITEM";

	public static final String TYPE_DAMAGE = "DAMAGE";

	public static final String TYPE_REPLACE = "REPLACE";

	public static final String TYPE_OTHER = "OTHER";

	public static final String STATUS_OPEN = "1";

	public static final String STATUS_PROCESSING = "2";

	public static final String STATUS_CLOSED = "3";

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/** 拣货波次ID */
	private String waveId;

	/** 订单ID */
	private String orderId;

	/** 配送任务ID */
	private String taskId;

	/** 异常类型：SHORT_PICK/WRONG_ITEM/DAMAGE/REPLACE/OTHER */
	private String exceptionType;

	/** 异常描述 */
	private String description;

	/** 证据图片URL（JSON数组） */
	private String evidenceUrls;

	/** 处理状态：1待处理 2处理中 3已关闭 */
	private String status;

	/** 处理人ID */
	private String handlerId;

	/** 处理说明 */
	private String handleRemark;

	/** 处理完成时间 */
	private LocalDateTime handledTime;

	private String tenantId;

	private String createBy;

	private String updateBy;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	@TableLogic
	private String delFlag;

}
