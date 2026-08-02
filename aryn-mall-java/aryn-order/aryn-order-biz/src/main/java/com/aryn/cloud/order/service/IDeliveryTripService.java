
package com.aryn.cloud.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.order.api.entity.DeliveryTrip;

/**
 * 出车单
 *
 * @author aryn
 * @since 2025/7/31
 */
public interface IDeliveryTripService extends IService<DeliveryTrip> {

	/**
	 * 出车单详情（含所有task和item）
	 * @param id 出车单ID
	 * @return 出车单
	 */
	DeliveryTrip getTripDetail(String id);

	/**
	 * 开始配货
	 * @param tripId 出车单ID
	 * @param staffId 当前配送员ID
	 * @return 是否成功
	 */
	boolean startLoading(String tripId, String staffId);

	/**
	 * 装货完毕出发
	 * @param tripId 出车单ID
	 * @param staffId 当前配送员ID
	 * @return 是否成功
	 */
	boolean depart(String tripId, String staffId);

	/**
	 * 调整送货顺序
	 * @param tripId 出车单ID
	 * @param staffId 当前配送员ID
	 * @param taskIds 按顺序排列的任务ID
	 * @return 是否成功
	 */
	boolean adjustSort(String tripId, String staffId, java.util.List<String> taskIds);

	/**
	 * 获取配送员当前进行中的出车单
	 * @param staffId 配送员ID
	 * @return 出车单
	 */
	DeliveryTrip getActiveTrip(String staffId);

}