package com.aryn.cloud.user.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 积分配置
 *
 * @author 雨滴kian
 */
@Data
@Schema(description = "积分配置")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "points_config")
public class PointsConfig extends Model<PointsConfig> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "规则名称")
	private String ruleName;

	@Schema(description = "规则类型：1-获取；2-消耗")
	private String ruleType;

	@Schema(description = "触发场景")
	private String triggerScene;

	@Schema(description = "积分值")
	private Integer pointValue;

	@Schema(description = "状态：0-启用；1-禁用")
	private String status;

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

	@Schema(description = "租户ID")
	private String tenantId;

}
