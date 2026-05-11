package com.aryn.cloud.user.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 积分记录
 *
 * @author 雨滴kian
 */
@Data
@Schema(description = "积分记录")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "points_record")
public class PointsRecord extends Model<PointsRecord> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "用户ID")
	private String userId;

	@Schema(description = "变动类型：1-获取；2-消耗")
	private String changeType;

	@Schema(description = "变动积分")
	private Integer changePoint;

	@Schema(description = "变动后余额")
	private Integer balanceAfter;

	@Schema(description = "触发场景")
	private String triggerScene;

	@Schema(description = "备注")
	private String remark;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "租户ID")
	private String tenantId;

}
