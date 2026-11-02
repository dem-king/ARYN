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
import java.time.LocalTime;

@Data
@Schema(description = "秒杀每日排期模板")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "seckill_session_template")
public class SeckillSessionTemplate extends Model<SeckillSessionTemplate> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "活动ID")
	@NotBlank(message = "活动ID不能为空")
	private String activityId;

	@Schema(description = "场次名称")
	@NotBlank(message = "场次名称不能为空")
	private String sessionName;

	@Schema(description = "每日开始时间")
	@NotNull(message = "场次开始时间不能为空")
	private LocalTime startTime;

	@Schema(description = "每日结束时间")
	@NotNull(message = "场次结束时间不能为空")
	private LocalTime endTime;

	/** 生效星期：1-7 逗号分隔，1=周一；空/全选表示每天 */
	@Schema(description = "生效星期(1-7逗号分隔，1=周一)")
	private String weekdays;

	@Schema(description = "状态:0启用 1停用")
	private Integer status;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除:0正常,1删除")
	private String delFlag;

	@Schema(description = "租户ID")
	private String tenantId;
}
