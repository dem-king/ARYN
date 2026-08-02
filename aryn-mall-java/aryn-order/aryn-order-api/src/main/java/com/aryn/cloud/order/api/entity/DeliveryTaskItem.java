
package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 取货明细
 *
 * @author aryn
 * @since 2025/7/31
 */
@Data
@Schema(description = "取货明细")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "delivery_task_item")
public class DeliveryTaskItem extends Model<DeliveryTaskItem> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "关联配送任务ID")
	private String taskId;

	@Schema(description = "关联订单明细ID")
	private String orderItemId;

	@Schema(description = "商品名称快照")
	private String spuName;

	@Schema(description = "规格名称快照")
	private String skuName;

	@Schema(description = "应取数量")
	private Integer quantity;

	@Schema(description = "商品图片快照")
	private String image;

	@Schema(description = "0未取 1已取")
	private String picked;

	@Schema(description = "确认取货时间")
	private LocalDateTime pickedTime;

	@Schema(description = "当前尝试号")
	private Integer attemptNo;

	@Schema(description = "租户ID")
	private String tenantId;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改人")
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;

}