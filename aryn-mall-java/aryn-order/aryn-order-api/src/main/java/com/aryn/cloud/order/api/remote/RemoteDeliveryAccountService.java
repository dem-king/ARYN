
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

	/**
	 * 同步员工账号手机号到该员工的配送员资料（staff_phone 资料快照）
	 *
	 * <p>租户隔离由调用链上下文（ArynTenantContextHolder）保证：调用方必须在员工账号
	 * 自己的租户上下文内调用，否则会定位到错误租户的配送资料。员工账号手机号是登录凭据，
	 * 本地更新后立即生效，本方法只维护配送工作台展示/搜索用的联系电话快照，
	 * 调用方按 best-effort 处理，同步失败不回滚手机号修改。</p>
	 * @param sysUserId 员工账号ID
	 * @param phone 新手机号
	 * @return 手机号或员工账号为空、无未删除的配送员资料时返回 false；
	 * 号码相同直接返回 true；否则返回资料更新结果
	 */
	boolean syncStaffPhone(String sysUserId, String phone);

}
