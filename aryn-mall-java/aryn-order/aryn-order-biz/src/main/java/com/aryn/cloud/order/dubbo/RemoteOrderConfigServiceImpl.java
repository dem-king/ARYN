package com.aryn.cloud.order.dubbo;

import com.aryn.cloud.order.api.entity.OrderConfig;
import com.aryn.cloud.order.api.remote.RemoteOrderConfigService;
import com.aryn.cloud.order.service.IOrderConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 订单配置 Dubbo 远程实现
 */
@Service
@org.apache.dubbo.config.annotation.DubboService
@RequiredArgsConstructor
public class RemoteOrderConfigServiceImpl implements RemoteOrderConfigService {

	private final IOrderConfigService orderConfigService;

	@Override
	public String getNotifyUrl() {
		OrderConfig orderConfig = orderConfigService.getConfig();
		return orderConfig == null ? null : orderConfig.getNotifyUrl();
	}

}
