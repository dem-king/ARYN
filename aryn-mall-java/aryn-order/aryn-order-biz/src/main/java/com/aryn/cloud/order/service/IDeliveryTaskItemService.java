
package com.aryn.cloud.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.order.api.entity.DeliveryTaskItem;

import java.util.List;

/**
 * 取货明细
 *
 * @author aryn
 * @since 2025/7/31
 */
public interface IDeliveryTaskItemService extends IService<DeliveryTaskItem> {

	/**
	 * 根据任务ID查询取货明细
	 * @param taskId 任务ID
	 * @return 明细列表
	 */
	List<DeliveryTaskItem> listByTaskId(String taskId);

	/**
	 * 逐项确认取货
	 * @param itemId 明细ID
	 * @param staffId 当前配送员ID
	 * @return 是否成功
	 */
	boolean pick(String itemId, String staffId);

	/**
	 * 取消确认取货
	 * @param itemId 明细ID
	 * @param staffId 当前配送员ID
	 * @return 是否成功
	 */
	boolean unpick(String itemId, String staffId);

	/**
	 * 校验某出车单下所有任务的所有明细是否全部已取
	 * @param tripId 出车单ID
	 * @return 是否全部已取
	 */
	boolean allPicked(String tripId);

}