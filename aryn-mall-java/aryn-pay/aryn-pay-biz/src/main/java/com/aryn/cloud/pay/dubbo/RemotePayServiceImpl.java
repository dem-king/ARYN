
package com.aryn.cloud.pay.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.pay.api.dto.CreateOrderReqDTO;
import com.aryn.cloud.pay.api.dto.PaySettlementResult;
import com.aryn.cloud.pay.api.remote.RemotePayService;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.handler.PayOrderHandler;
import com.aryn.cloud.pay.service.IPayNotifyRecordService;
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

	private final IPayNotifyRecordService payNotifyRecordService;

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

	@Override
	public PaySettlementResult queryAndSettlePayOrder(String outTradeNo) {
		// 消费方租户由 Dubbo 过滤器透传（boot 模式为同线程 injvm），这里显式取出交给服务层
		return payNotifyRecordService.queryAndSettlePayOrder(ArynTenantContextHolder.getTenantId(), outTradeNo);
	}

}
