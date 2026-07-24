
package com.aryn.cloud.pay.dubbo;

import com.aryn.cloud.pay.api.dto.CreateRefundsReqDTO;
import com.aryn.cloud.pay.api.remote.RemoteRefundService;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.handler.PayRefundOrderHandler;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * @author 雨滴kian
 * @description
 * @date 2024/11/23
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteRefundServiceImpl implements RemoteRefundService {

	private final Map<String, PayRefundOrderHandler> payRefundOrderHandlerMap;

	@Override
	public Object refunds(CreateRefundsReqDTO createRefundsReqDTO) {
		if (createRefundsReqDTO == null) {
			throw new ArynBusinessException("退款请求不能为空");
		}
		PayRefundOrderHandler handler = payRefundOrderHandlerMap.get(createRefundsReqDTO.getRefundType());
		if (handler == null) {
			throw new ArynBusinessException("不支持的退款方式");
		}
		return handler.refund(createRefundsReqDTO);
	}

}
