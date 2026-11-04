
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

	@Schema(description = "部门名称（关联查询字段，非表字段）")
	@TableField(exist = false)
	private String deptName;

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

	/**
	 * 租户类型：1.平台租户；2.普通租户（非表字段）。
	 *
	 * <p>登录与用户信息查询时按 sys_tenant.tenant_type 填充，供会话快照和前端
	 * 识别平台管理员；不落库，也不允许通过接口写入。</p>
	 */
	@TableField(exist = false)
	@Schema(description = "租户类型：1.平台租户；2.普通租户（非表字段）")
	private String tenantType;

	/**
	 * 是否平台管理员（非表字段）。
	 *
	 * <p>登录与用户信息查询时按 {@code sys_user.platform_admin} + 所属租户类型计算后填充，
	 * 供会话快照与前端识别平台管理员；不落库，也不允许通过接口写入。</p>
	 */
	@TableField(exist = false)
	@Schema(description = "是否平台管理员（非表字段）")
	private Boolean platformAdmin;

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
