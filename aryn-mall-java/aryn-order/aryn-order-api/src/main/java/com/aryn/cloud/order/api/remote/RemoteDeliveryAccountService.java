
package com.aryn.cloud.order.api.remote;

import com.aryn.cloud.order.api.dto.DeliveryEligibilityDTO;

/**
 * 配送账号绑定远程服务（Dubbo）
 *
 * @author aryn
 * @since 2026/9/5
 */
public interface RemoteDeliveryAccountService {

	/**
	 * 按商城用户查询有效配送绑定与待处理任务数
	 *
	 * <p>租户隔离由调用链上下文（ArynTenantContextHolder）保证，
	 * 未绑定或绑定已解绑时返回 bound=false 的空结果，不抛异常。
	 * @param mallUserId 商城用户ID
	 * @return 资格原始信息
	 */
	DeliveryEligibilityDTO getEligibilityByMallUser(String mallUserId);

	/**
	 * 判断员工账号是否仍存在有效配送员资料（删除员工账号前的跨域保护检查）
	 *
	 * <p>租户隔离由调用链上下文保证；查询异常由调用方 fail-closed 处理。
	 * @param sysUserId 员工账号ID
	 * @return true 表示仍存在未删除的配送员资料，禁止直接删除员工账号
	 */
	boolean hasActiveDeliveryStaff(String sysUserId);

}
