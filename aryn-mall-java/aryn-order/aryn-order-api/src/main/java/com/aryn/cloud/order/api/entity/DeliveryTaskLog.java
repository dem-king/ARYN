
package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 配送任务操作日志
 *
 * @author aryn
 * @since 2025/7/31
 */
@Data
@Schema(description = "配送任务操作日志")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "delivery_task_log")
public class DeliveryTaskLog extends Model<DeliveryTaskLog> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "关联配送任务ID")
	private String taskId;

	@Schema(description = "动作")
	private String action;

	@Schema(description = "原状态")
	private String fromStatus;

	@Schema(description = "目标状态")
	private String toStatus;

	@Schema(description = "尝试号")
	private Integer attemptNo;

	@Schema(description = "操作人类型：1管理员 2配送员 3系统")
	private String operatorType;

	@Schema(description = "操作人ID")
	private String operatorId;

	@Schema(description = "操作人姓名快照")
	private String operatorName;

	@Schema(description = "原因编码")
	private String reasonCode;

	@Schema(description = "说明")
	private String reasonDesc;

	@Schema(description = "受控JSON摘要")
	private String summary;

	@Schema(description = "租户ID")
	private String tenantId;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;

}