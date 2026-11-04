package com.aryn.cloud.upms.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 管理员为员工账号修改手机号的请求。
 *
 * <p>手机号是管理端短信登录与配送员登录的凭据，换号等价于账号接管级敏感操作：
 * 由独立权限 {@code upms:sysuser:phone:edit} 控制，只接受完整号码，
 * 格式、同号与跨租户唯一性一律由服务端校验。</p>
 *
 * @author aryn
 * @since 2026/11/2
 */
@Data
@Schema(description = "员工手机号修改请求")
public class SysUserPhoneUpdateDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "新的手机号")
	private String phone;

}
