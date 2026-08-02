
package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 送达凭证
 *
 * @author aryn
 * @since 2025/7/31
 */
@Data
@Schema(description = "送达凭证")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "delivery_evidence")
public class DeliveryEvidence extends Model<DeliveryEvidence> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "关联配送任务ID")
	private String taskId;

	@Schema(description = "尝试号")
	private Integer attemptNo;

	@Schema(description = "凭证类型：1送达 2异常 3退回")
	private String evidenceType;

	@Schema(description = "关联素材ID")
	private String materialId;

	@Schema(description = "素材访问URL快照")
	private String materialUrl;

	@Schema(description = "排序")
	private Integer sortNo;

	@Schema(description = "上传人ID")
	private String uploadBy;

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