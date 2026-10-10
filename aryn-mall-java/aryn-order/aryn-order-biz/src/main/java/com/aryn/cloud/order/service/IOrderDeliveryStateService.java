
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

	/**
	 * 判断货物是否已送达（不再依赖买家在小程序点「确认收货」）。
	 *
	 * <p>口径：
	 * <ul>
	 * <li>订单已完成（买家确认收货或超时自动收货）：已送达；</li>
	 * <li>商城配送/内部配送（way=3/4）：配送任务为「已送达」或「已签收」即视为已送达；</li>
	 * <li>上门自提（way=2）：到店即交付，订单进入待收货即视为已送达；</li>
	 * <li>第三方快递（way=1）：无客观妥投信号，只能以买家确认收货为准，即仅已完成算已送达。</li>
	 * </ul>
	 *
	 * <p>货到付款确认收款以此为前提：客户当面付款常先于小程序点确认收货，
	 * 故「已送达」而非「已完成」才是收款的可靠信号。
	 *
	 * @param orderInfo 订单（需含 status/deliveryWay/deliveryTask）
	 * @return 是否已送达
	 */
	boolean isDelivered(com.aryn.cloud.order.api.entity.OrderInfo orderInfo);

	/**
	 * 买家能否确认收货（C 端「确认收货」按钮与接口的守卫口径）。
	 *
	 * <p>与 {@link #isDelivered} 的区别在于第三方快递：快递没有内部配送任务、
	 * 也没有客观妥投信号，只能由买家自行核对后确认，故这里放行；
	 * 而 isDelivered 对快递只认「已完成」，用于货到付款收款等需要客观送达证据的场景。
	 *
	 * <p>口径：
	 * <ul>
	 * <li>商城配送/内部配送（way=3/4）：配送任务必须为「已送达」或「已签收」。
	 * fail-closed——任务缺失或未送达一律拒绝，避免司机还没点已送达买家就能确认收货；</li>
	 * <li>上门自提（way=2）：到店即交付，放行；</li>
	 * <li>第三方快递（way=1）：放行，由买家自行核对；</li>
	 * <li>订单已完成：放行（幂等，重复确认不再报错）。</li>
	 * </ul>
	 *
	 * @param orderInfo 订单（需含 status/deliveryWay；deliveryTask 可为空，为空时按订单ID实时查询）
	 * @return 是否允许确认收货
	 */
	boolean isReadyToReceive(com.aryn.cloud.order.api.entity.OrderInfo orderInfo);

}
