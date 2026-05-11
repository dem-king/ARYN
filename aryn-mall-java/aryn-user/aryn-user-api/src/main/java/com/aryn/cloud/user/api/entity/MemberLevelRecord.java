package com.aryn.cloud.user.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 会员等级变更记录
 *
 * @author 雨滴kian
 */
@Data
@Schema(description = "会员等级变更记录")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "member_level_record")
public class MemberLevelRecord extends Model<MemberLevelRecord> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "用户ID")
	private String userId;

	@Schema(description = "原等级ID")
	private String oldLevelId;

	@Schema(description = "新等级ID")
	private String newLevelId;

	@Schema(description = "变更原因")
	private String changeReason;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "租户ID")
	private String tenantId;

}
