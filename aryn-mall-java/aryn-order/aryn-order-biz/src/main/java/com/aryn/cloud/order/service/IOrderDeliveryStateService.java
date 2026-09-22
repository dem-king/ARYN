
package com.aryn.cloud.order.service;

import com.aryn.cloud.order.api.entity.DeliveryTask;

/**
 * 商城配送/内部配送的订单状态联动。
 *
 * <p>delivery_way=3（商城配送）与 delivery_way=4（公司港口/船舶内部配送）
 * 由配送任务驱动订单状态：确认取货出发后订单转「待收货」，
 * 客户签收或超时后订单转「已完成」。
 *
 * @author aryn
 * @since 2026/9/22
 */
public interface IOrderDeliveryStateService {

	/**
	 * 配送任务出发（确认取货）时，把订单从「待发货」推进到「待收货」，
	 * 并把订单项从「待发货」推进到「已发货」，记录发货时间。
	 * @param task 已出发的配送任务
	 * @return 是否发生状态推进（幂等：已是待收货返回 true）
	 */
	boolean markShippedOnPickUp(DeliveryTask task);

	/**
	 * 判断订单是否由配送任务驱动（区别于第三方快递的发货单驱动）。
	 * @param orderId 订单ID
	 * @return 是否任务驱动
	 */
	boolean isTaskDrivenOrder(String orderId);

}
