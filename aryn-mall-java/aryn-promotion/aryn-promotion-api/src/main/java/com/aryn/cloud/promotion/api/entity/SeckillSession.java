package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@Schema(description = "秒杀场次")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "seckill_session")
public class SeckillSession extends Model<SeckillSession> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "活动ID")
	@NotBlank(message = "活动ID不能为空")
	private String activityId;

	@Schema(description = "场次名称")
	@NotBlank(message = "场次名称不能为空")
	private String sessionName;

	@Schema(description = "场次开始时间")
	@NotNull(message = "场次开始时间不能为空")
	private LocalDateTime startTime;

	@Schema(description = "场次结束时间")
	@NotNull(message = "场次结束时间不能为空")
	private LocalDateTime endTime;

	@Schema(description = "状态:0未开始 1进行中 2已结束")
	private Integer status;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除:0正常,1删除")
	private String delFlag;

	@Schema(description = "租户ID")
	private String tenantId;
}