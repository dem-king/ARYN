package com.aryn.cloud.promotion.dubbo;

import com.aryn.cloud.promotion.api.remote.RemoteGroupBuyService;
import com.aryn.cloud.promotion.api.vo.GroupBuyOrderContextVO;
import com.aryn.cloud.promotion.service.IGroupBuyRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * 拼团 Dubbo 远程服务实现（供订单服务拼团下单链路调用）
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteGroupBuyServiceImpl implements RemoteGroupBuyService {

	private final IGroupBuyRecordService groupBuyRecordService;

	@Override
	public GroupBuyOrderContextVO getOrderContext(String recordId, String userId) {
		return groupBuyRecordService.getOrderContext(recordId, userId);
	}

	@Override
	public boolean bindOrder(String recordId, String userId, String orderId) {
		return groupBuyRecordService.bindOrder(recordId, userId, orderId);
	}

	@Override
	public boolean releaseOrder(String orderId) {
		return groupBuyRecordService.releaseOrder(orderId);
	}
}
