
package com.aryn.cloud.pay.handler;

import com.aryn.cloud.pay.api.dto.CreateOrderReqDTO;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import lombok.extern.slf4j.Slf4j;

/**
 * 支付抽象类
 *
 * @author 雨滴kian
 * @since 2023/4/18 19:11
 */
@Slf4j
public abstract class AbstractPayOrderHandler implements PayOrderHandler {

	@Override
	public Object pay(CreateOrderReqDTO createOrderReqDTO) {
		// 参数检查
		validateRequest(createOrderReqDTO);
		// 创建订单
		PayTradeOrder payTradeOrder = createOrder(createOrderReqDTO);
		// 支付逻辑
		Object result = doPay(payTradeOrder);
		// 后置处理
		postPay(payTradeOrder);
		return result;
	}

	public abstract Object doPay(PayTradeOrder payTradeOrder);

	private void validateRequest(CreateOrderReqDTO createOrderReqDTO) {
		// 参数检查
	}

	private void postPay(PayTradeOrder payTradeOrder) {
		// 后置处理
	}

}
