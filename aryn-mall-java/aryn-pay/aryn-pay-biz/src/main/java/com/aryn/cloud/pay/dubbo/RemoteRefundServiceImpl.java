
package com.aryn.cloud.pay.dubbo;

import com.aryn.cloud.pay.api.dto.CreateRefundsReqDTO;
import com.aryn.cloud.pay.api.remote.RemoteRefundService;
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
		return payRefundOrderHandlerMap.get(createRefundsReqDTO.getRefundType()).refund(createRefundsReqDTO);
	}

}
