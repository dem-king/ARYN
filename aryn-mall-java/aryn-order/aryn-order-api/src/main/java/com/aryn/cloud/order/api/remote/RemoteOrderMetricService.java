package com.aryn.cloud.order.api.remote;

/**
 * 订单指标 Dubbo 远程服务。
 *
 * <p>供平台侧租户概览读取订单域计数，租户由调用上下文决定。
 */
public interface RemoteOrderMetricService {

	/**
	 * 统计当前租户订单总数。
	 */
	Long countOrders();

}
