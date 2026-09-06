
package com.aryn.cloud.upms.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import com.aryn.cloud.common.core.annotation.Desensitization;
import com.aryn.cloud.common.core.desensitization.MobilePhoneDesensitization;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 系统用户
 *
 * @author 雨滴kian
 * @since 2022/2/10 11:00
 */
@Data
@Schema(description = "系统用户")
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_user")
public class SysUser extends Model<SysUser> {

	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "用户名")
	private String username;

	@Schema(description = "密码")
	private String password;

	@Schema(description = "邮箱")
	private String email;

	@Schema(description = "用户昵称")
	private String nickname;

	@Schema(description = "头像")
	private String avatar;

	@Schema(description = "部门ID")
	private String deptId;

	@Schema(description = "手机号")
	@Desensitization(MobilePhoneDesensitization.class)
	private String phone;

	@Schema(description = "状态：0.正常；1.停用；")
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

	@Schema(description = "租户id")
	private String tenantId;

	@Schema(description = "账号类型：0.系统主账户（管理全部店铺）")
	private String type;

	/** 权限标识 */
	@TableField(exist = false)
	private Set<String> permissions;

	/** 角色 */
	@TableField(exist = false)
	private List<String> roles;

	/** 配送资格只读摘要：是否拥有受保护配送角色（delivery_staff），仅员工账号列表展示用 */
	@TableField(exist = false)
	private Boolean deliveryQualification;

}
