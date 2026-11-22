package com.aryn.cloud.order.dubbo;

import com.aryn.cloud.order.api.remote.RemoteOrderMetricService;
import com.aryn.cloud.order.api.vo.EnterprisePurchaseAggVO;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.order.service.IOrderInfoService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 订单指标 Dubbo 远程实现。
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteOrderMetricServiceImpl implements RemoteOrderMetricService {

	private final IOrderInfoService orderInfoService;

	private final OrderInfoMapper orderInfoMapper;

	@Override
	public Long countOrders() {
		return orderInfoService.count();
	}

	@Override
	public List<EnterprisePurchaseAggVO> aggregateEnterprisePurchase(String tenantId, String startMonth) {
		if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(startMonth)) {
			return List.of();
		}
		// 查询失败必须抛出（不能吞成空列表）：调用方以空列表为「合法零采购」覆盖 growth，
		// 吞异常会把存量主体误清零降级；抛出后由调用方回退存量绑定重算
		return orderInfoMapper.aggregateEnterprisePurchase(tenantId, startMonth);
	}

}
