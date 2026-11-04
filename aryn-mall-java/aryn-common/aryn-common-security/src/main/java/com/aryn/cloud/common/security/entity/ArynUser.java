
package com.aryn.cloud.common.security.entity;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@NoArgsConstructor
public class ArynUser implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "用户id")
	private String userId;

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
	private String phone;

	@Schema(description = "状态：0.正常；1.停用；")
	private String status;

	@Schema(description = "租户id")
	private String tenantId;

	/**
	 * 租户类型：1.平台租户；2.普通租户。
	 *
	 * <p>平台身份以数据（sys_tenant.tenant_type）为准，登录时随会话快照写入；
	 * 租户切换、平台配置守卫、菜单过滤均按此字段判断，不要再硬编码租户 ID。
	 * 存量会话没有该字段时按普通租户处理，重新登录即可刷新。</p>
	 */
	@Schema(description = "租户类型：1.平台租户；2.普通租户")
	private String tenantType;

	/**
	 * 是否平台管理员（用户级身份，唯一判据）。
	 *
	 * <p>登录时按 {@code sys_user.platform_admin = '1'} 且所属租户
	 * {@code sys_tenant.tenant_type = '1'} 计算后写入会话快照；租户管理员即使位于平台租户
	 * 也不是平台管理员。租户切换、平台配置守卫、平台菜单/套餐豁免均按此字段判断。</p>
	 */
	@Schema(description = "是否平台管理员")
	private Boolean platformAdmin;

	@Schema(description = "openId")
	private String openId;

	@Schema(description = "账号类型：0.系统主账户")
	private String type;

	@Schema(description = "登录端类型：TOB 后台，TOC 商城")
	private DeviceTypeEnum deviceType;

	/** 权限标识 */
	private List<String> permissions;

	/** 角色 */
	private List<String> roles;

}
