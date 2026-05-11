
package com.aryn.cloud.pay.handler;

import com.aryn.cloud.pay.api.dto.CreateRefundsReqDTO;
import com.aryn.cloud.pay.api.entity.PayRefundOrder;
import lombok.extern.slf4j.Slf4j;

/**
 * 支付抽象类
 *
 * @author 雨滴kian
 * @since 2023/4/18 19:11
 */
@Slf4j
public abstract class AbstractPayRefundOrderHandler implements PayRefundOrderHandler {

	@Override
	public Object refund(CreateRefundsReqDTO createRefundsReqDTO) {
		// 参数检查
		validateRequest(createRefundsReqDTO);
		// 创建订单
		PayRefundOrder payRefundOrder = createRefundOrder(createRefundsReqDTO);
		// 退款逻辑
		Object result = doRefund(payRefundOrder);
		// 后置处理
		postPay(payRefundOrder);
		return result;
	}

	public abstract Object doRefund(PayRefundOrder payRefundOrder);

	private void validateRequest(CreateRefundsReqDTO createRefundsReqDTO) {
		// 参数检查
	}

	private void postPay(PayRefundOrder payRefundOrder) {
		// 后置处理
	}

}
