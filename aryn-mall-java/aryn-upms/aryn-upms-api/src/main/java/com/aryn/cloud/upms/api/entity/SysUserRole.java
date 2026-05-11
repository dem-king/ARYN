
package com.aryn.cloud.upms.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 系统用户关联角色
 *
 * @author 雨滴kian
 * @since 2022/2/10 11:00
 */
@Data
@Schema(description = "系统用户关联角色")
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_user_role")
public class SysUserRole extends Model<SysUserRole> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "用户ID")
	private String userId;

	@Schema(description = "角色ID")
	private String roleId;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "租户id")
	private String tenantId;

}
