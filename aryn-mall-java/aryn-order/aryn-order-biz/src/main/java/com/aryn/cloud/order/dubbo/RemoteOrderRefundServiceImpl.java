package com.aryn.cloud.order.dubbo;

import com.aryn.cloud.order.api.remote.RemoteOrderRefundService;
import com.aryn.cloud.order.service.IOrderRefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 订单退款 Dubbo 远程实现（供营销服务发起系统侧自动退款）
 */
@Service
@org.apache.dubbo.config.annotation.DubboService
@RequiredArgsConstructor
public class RemoteOrderRefundServiceImpl implements RemoteOrderRefundService {

	private final IOrderRefundService orderRefundService;

	@Override
	public boolean refundWholeOrder(String orderId, String reason) {
		return orderRefundService.refundWholeOrder(orderId, reason);
	}
}
