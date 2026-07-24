
package com.aryn.cloud.pay.dubbo;

import com.aryn.cloud.pay.api.dto.CreateOrderReqDTO;
import com.aryn.cloud.pay.api.remote.RemotePayService;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.handler.PayOrderHandler;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author 雨滴kian
 * @date 2024/11/23
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemotePayServiceImpl implements RemotePayService {

	private final Map<String, PayOrderHandler> payOrderHandlerMap;

	@Override
	public Object createOrder(CreateOrderReqDTO createOrderReqDTO) {
		if (createOrderReqDTO == null) {
			throw new ArynBusinessException("支付请求不能为空");
		}
		PayOrderHandler handler = payOrderHandlerMap.get(createOrderReqDTO.getTradeType());
		if (handler == null) {
			throw new ArynBusinessException("不支持的支付方式");
		}
		return handler.pay(createOrderReqDTO);
	}

}
