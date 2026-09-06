
package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 配送员管理列表行（含绑定与权限摘要）
 *
 * @author aryn
 * @since 2026/9/5
 */
@Data
@Schema(description = "配送员管理列表行")
public class DeliveryStaffManagerVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "配送员ID")
	private String id;

	@Schema(description = "员工账号ID（sys_user.id）")
	private String userId;

	@Schema(description = "配送员姓名")
	private String staffName;

	@Schema(description = "配送员手机号")
	private String staffPhone;

	@Schema(description = "接单状态：1在线 2忙碌 3离线")
	private String status;

	@Schema(description = "车辆信息")
	private String vehicleInfo;

	@Schema(description = "员工账号昵称/用户名")
	private String sysUserName;

	@Schema(description = "员工账号状态：0正常 1停用")
	private String sysUserStatus;

	@Schema(description = "员工是否拥有配送执行权限")
	private Boolean deliveryPermission;

	@Schema(description = "绑定状态：bound已绑定 unbound未绑定")
	private String bindingStatus;

	@Schema(description = "商城用户ID")
	private String mallUserId;

	@Schema(description = "商城用户昵称")
	private String mallUserNickname;

	@Schema(description = "商城用户手机号（脱敏）")
	private String mallUserPhone;

	@Schema(description = "绑定时间")
	private LocalDateTime bindTime;

	@Schema(description = "进行中任务数（待取货/配货中/待送达）")
	private Integer pendingTaskCount;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

}
