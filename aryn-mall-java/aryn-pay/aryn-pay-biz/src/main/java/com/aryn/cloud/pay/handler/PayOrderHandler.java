
package com.aryn.cloud.pay.handler;

import com.aryn.cloud.pay.api.dto.CreateOrderReqDTO;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;

public interface PayOrderHandler {

	/**
	 * 创建订单
	 * @param createOrderReqDTO 预支付参数
	 * @return obj
	 */
	PayTradeOrder createOrder(CreateOrderReqDTO createOrderReqDTO);

	/**
	 * 调起渠道支付
	 * @param createOrderReqDTO 预支付参数
	 * @return obj
	 */
	Object pay(CreateOrderReqDTO createOrderReqDTO);

}
