
package com.aryn.cloud.order.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.order.api.entity.OrderDelivery;
import com.kuaidi100.sdk.response.SubscribePushParamResp;

/**
 * 发货单
 *
 * @author 雨滴kian
 * @since 2022/3/7 14:18
 */
public interface IOrderDeliveryService extends IService<OrderDelivery> {

	/**
	 * 物流轨迹回调
	 * @param orderDelivery
	 * @param subscribePushParamResp
	 */
	void notifyLogistics(OrderDelivery orderDelivery, SubscribePushParamResp subscribePushParamResp);

	/**
	 * 根据订单号获取发货单
	 * @param orderId
	 * @return
	 */
	OrderDelivery getByOrderId(String orderId);

}
