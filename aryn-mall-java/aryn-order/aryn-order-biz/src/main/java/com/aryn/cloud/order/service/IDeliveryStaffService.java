
package com.aryn.cloud.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.order.api.entity.DeliveryStaff;

/**
 * 配送员
 *
 * @author aryn
 * @since 2025/7/31
 */
public interface IDeliveryStaffService extends IService<DeliveryStaff> {

	/**
	 * 更新配送员状态
	 * @param id 配送员ID
	 * @param status 目标状态
	 * @return 是否成功
	 */
	boolean updateStatus(String id, String status);

	/**
	 * 根据后台用户ID获取配送员
	 * @param userId 后台用户ID
	 * @return 配送员
	 */
	DeliveryStaff getByUserId(String userId);

}