
package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 配送员资料 VO（C 端返回，不暴露租户、审计和逻辑删除字段）
 *
 * @author aryn
 * @since 2026/9/5
 */
@Data
@Schema(description = "配送员资料VO")
public class DeliveryStaffVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "配送员ID")
	private String id;

	@Schema(description = "配送员姓名")
	private String staffName;

	@Schema(description = "手机号")
	private String staffPhone;

	@Schema(description = "接单状态：1在线 2忙碌 3离线")
	private String status;

	@Schema(description = "车辆信息")
	private String vehicleInfo;

}
