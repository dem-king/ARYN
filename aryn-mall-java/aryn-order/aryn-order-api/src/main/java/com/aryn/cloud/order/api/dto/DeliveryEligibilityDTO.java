
package com.aryn.cloud.order.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商城用户配送资格原始信息（订单域查询结果）
 *
 * <p>bindingStatus 使用明确状态而不是多个布尔值：
 * 员工账号状态与配送权限由认证服务结合 upms 数据最终判定，
 * 订单域只对绑定与配送员资料的一致性负责。
 *
 * @author aryn
 * @since 2026/9/5
 */
@Data
@Schema(description = "商城用户配送资格原始信息")
public class DeliveryEligibilityDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/** 存在有效绑定，且配送员资料存在、归属一致 */
	public static final String BINDING_BOUND = "BOUND";

	/** 没有有效绑定 */
	public static final String BINDING_UNBOUND = "UNBOUND";

	/** 配送员资料不存在、已删除或与绑定归属不一致 */
	public static final String BINDING_STAFF_INVALID = "STAFF_INVALID";

	@Schema(description = "绑定侧结论：BOUND/UNBOUND/STAFF_INVALID")
	private String bindingStatus;

	@Schema(description = "绑定记录租户ID（供认证服务显式比对，空视为无法确认租户）")
	private String tenantId;

	@Schema(description = "员工账号ID（sys_user.id）")
	private String sysUserId;

	@Schema(description = "配送员资料ID（delivery_staff.id）")
	private String deliveryStaffId;

	@Schema(description = "配送员姓名")
	private String staffName;

	@Schema(description = "接单状态：1在线 2忙碌 3离线")
	private String staffStatus;

	@Schema(description = "待处理任务数（待取货/配货中/待送达）")
	private Integer pendingTaskCount;

	public static DeliveryEligibilityDTO unbound() {
		DeliveryEligibilityDTO dto = new DeliveryEligibilityDTO();
		dto.setBindingStatus(BINDING_UNBOUND);
		return dto;
	}

}
