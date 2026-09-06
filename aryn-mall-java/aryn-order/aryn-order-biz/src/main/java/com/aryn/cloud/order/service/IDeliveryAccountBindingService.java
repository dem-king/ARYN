
package com.aryn.cloud.order.service;

import com.aryn.cloud.order.api.entity.DeliveryAccountBinding;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 配送员商城账号绑定
 *
 * @author aryn
 * @since 2026/9/5
 */
public interface IDeliveryAccountBindingService extends IService<DeliveryAccountBinding> {

	/**
	 * 查询商城用户的当前有效绑定
	 * @param mallUserId 商城用户ID
	 * @return 有效绑定，未绑定返回 null
	 */
	DeliveryAccountBinding getActiveByMallUser(String mallUserId);

	/**
	 * 查询商城用户的绑定行（含已解绑，用于重新绑定复用行）
	 * @param mallUserId 商城用户ID
	 * @return 绑定行，从未绑定返回 null
	 */
	DeliveryAccountBinding getByMallUser(String mallUserId);

	/**
	 * 查询员工账号的当前有效绑定
	 * @param sysUserId 员工账号ID
	 * @return 有效绑定，未绑定返回 null
	 */
	DeliveryAccountBinding getActiveBySysUser(String sysUserId);

	/**
	 * 查询配送员的当前有效绑定
	 * @param staffId 配送员ID
	 * @return 有效绑定，未绑定返回 null
	 */
	DeliveryAccountBinding getActiveByStaffId(String staffId);

	/**
	 * 批量查询配送员的当前有效绑定
	 * @param staffIds 配送员ID集合
	 * @return staffId -> 绑定
	 */
	Map<String, DeliveryAccountBinding> getActiveByStaffIds(Collection<String> staffIds);

}
