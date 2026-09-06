
package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 向导式创建配送员请求
 *
 * @author aryn
 * @since 2026/9/5
 */
@Data
@Schema(description = "向导式创建配送员请求")
public class DeliveryOnboardDTO {

	@Schema(description = "员工账号ID（sys_user.id，已存在的账号）")
	@NotEmpty(message = "请选择员工账号")
	private String userId;

	@Schema(description = "配送员姓名（为空时取员工账号昵称）")
	private String staffName;

	@Schema(description = "手机号（为空时取员工账号手机号）")
	private String staffPhone;

	@Schema(description = "车辆信息")
	private String vehicleInfo;

	@Schema(description = "商城用户ID（选填，同时完成商城账号绑定）")
	private String mallUserId;

	@Schema(description = "接单状态：1在线 2忙碌 3离线（默认离线）")
	private String status;

	@Schema(description = "是否同时开通配送资格（授予配送员角色，默认 true）")
	private Boolean grantQualification;

}
