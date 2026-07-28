package com.aryn.cloud.order.api.delivery.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 商城配送任务。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("order_delivery_task")
public class OrderDeliveryTask extends Model<OrderDeliveryTask> {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;
	private String taskNo;
	private String orderId;
	private String orderNo;
	private String status;
	private String assigneeId;
	private String assigneeName;
	private String assigneeMobile;
	private String assignedBy;
	private String assignedByName;
	private Integer attemptNo;
	private Integer version;
	private LocalDateTime pickingStartedAt;
	private LocalDateTime pickedUpAt;
	private LocalDateTime deliveredAt;
	private LocalDateTime returnPendingAt;
	private LocalDateTime returnedAt;
	private LocalDateTime closedAt;
	private String exceptionCode;
	private String exceptionSummary;
	private String remark;
	private String tenantId;
	@TableField(fill = FieldFill.INSERT)
	private String createBy;
	@TableField(fill = FieldFill.UPDATE)
	private String updateBy;
	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;
	@TableField(fill = FieldFill.UPDATE)
	private LocalDateTime updateTime;
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	private String delFlag;

}
