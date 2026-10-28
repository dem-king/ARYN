package com.aryn.cloud.order.dubbo;

import com.aryn.cloud.order.api.remote.RemoteOrderMetricService;
import com.aryn.cloud.order.service.IOrderInfoService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * 订单指标 Dubbo 远程实现。
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteOrderMetricServiceImpl implements RemoteOrderMetricService {

	private final IOrderInfoService orderInfoService;

	@Override
	public Long countOrders() {
		return orderInfoService.count();
	}

}
