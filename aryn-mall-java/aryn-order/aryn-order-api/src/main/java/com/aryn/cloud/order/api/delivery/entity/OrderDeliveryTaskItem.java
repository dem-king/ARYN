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
 * 商城配送配货明细。
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("order_delivery_task_item")
public class OrderDeliveryTaskItem extends Model<OrderDeliveryTaskItem> {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;
	private String taskId;
	private String orderItemId;
	private Integer attemptNo;
	private String checked;
	private String checkedBy;
	private LocalDateTime checkedAt;
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
