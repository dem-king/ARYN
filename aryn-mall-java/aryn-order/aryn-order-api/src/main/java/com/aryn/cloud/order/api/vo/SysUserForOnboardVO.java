
package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 可选配送员工账号搜索结果（向导式创建用）
 *
 * @author aryn
 * @since 2026/9/5
 */
@Data
@Schema(description = "可选配送员工账号搜索结果")
public class SysUserForOnboardVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "员工账号ID（sys_user.id）")
	private String userId;

	@Schema(description = "员工昵称")
	private String nickname;

	@Schema(description = "登录用户名")
	private String username;

	@Schema(description = "手机号（脱敏）")
	private String phone;

	@Schema(description = "是否已存在配送员资料（已存在的账号不可重复选择）")
	private boolean deliveryExists;

	@Schema(description = "是否已开通配送执行权限")
	private boolean deliveryPermission;

}
