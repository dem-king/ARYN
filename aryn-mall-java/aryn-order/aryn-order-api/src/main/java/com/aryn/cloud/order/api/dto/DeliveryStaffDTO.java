
package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 配送员
 *
 * @author aryn
 * @since 2025/7/31
 */
@Data
public class DeliveryStaffDTO {

	@Schema(description = "主键")
	private String id;

	@Schema(description = "关联sys_user后台用户ID")
	private String userId;

	@Schema(description = "配送员姓名")
	@NotEmpty(message = "配送员姓名不能为空")
	private String staffName;

	@Schema(description = "手机号")
	@NotEmpty(message = "手机号不能为空")
	private String staffPhone;

	@Schema(description = "状态：1在线 2忙碌 3离线")
	private String status;

	@Schema(description = "车辆信息")
	private String vehicleInfo;

}