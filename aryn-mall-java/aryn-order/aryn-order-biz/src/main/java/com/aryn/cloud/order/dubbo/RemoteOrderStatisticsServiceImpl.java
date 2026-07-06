package com.aryn.cloud.order.dubbo;

import com.aryn.cloud.order.api.dto.OrderStatisticsDTO;
import com.aryn.cloud.order.api.remote.RemoteOrderStatisticsService;
import com.aryn.cloud.order.api.vo.CategoryRankVO;
import com.aryn.cloud.order.api.vo.OrderTradeStatisticsVO;
import com.aryn.cloud.order.api.vo.OrderTrendVO;
import com.aryn.cloud.order.service.IOrderStatisticsService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 远程订单统计服务实现 (Dubbo RPC)
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteOrderStatisticsServiceImpl implements RemoteOrderStatisticsService {

	private final IOrderStatisticsService orderStatisticsService;

	@Override
	public OrderTradeStatisticsVO getOrderOverview(OrderStatisticsDTO dto) {
		return orderStatisticsService.getOrderTradeStatistics(dto);
	}

	@Override
	public List<OrderTrendVO> getOrderTrend(OrderStatisticsDTO dto) {
		return orderStatisticsService.getOrderTradeTrend(dto);
	}

	@Override
	public List<CategoryRankVO> getCategoryRank(OrderStatisticsDTO dto) {
		return orderStatisticsService.getCategoryRank(dto);
	}

	@Override
	public Integer getRepurchaseUserCount(OrderStatisticsDTO dto) {
		return orderStatisticsService.getRepurchaseUserCount(dto);
	}

}