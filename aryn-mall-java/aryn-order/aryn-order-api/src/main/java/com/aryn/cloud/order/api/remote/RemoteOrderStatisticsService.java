package com.aryn.cloud.order.api.remote;

import com.aryn.cloud.order.api.dto.OrderStatisticsDTO;
import com.aryn.cloud.order.api.vo.CategoryRankVO;
import com.aryn.cloud.order.api.vo.OrderTradeStatisticsVO;
import com.aryn.cloud.order.api.vo.OrderTrendVO;

import java.util.List;

/**
 * 远程订单统计服务 (Dubbo RPC)
 */
public interface RemoteOrderStatisticsService {

	/**
	 * 获取订单概览统计 (GMV、订单数等)
	 * @param dto 统计参数
	 * @return OrderTradeStatisticsVO
	 */
	OrderTradeStatisticsVO getOrderOverview(OrderStatisticsDTO dto);

	/**
	 * 获取订单趋势统计
	 * @param dto 统计参数
	 * @return List<OrderTrendVO>
	 */
	List<OrderTrendVO> getOrderTrend(OrderStatisticsDTO dto);

	/**
	 * 获取品类销售排行 Top10
	 * @param dto 统计参数
	 * @return List<CategoryRankVO>
	 */
	List<CategoryRankVO> getCategoryRank(OrderStatisticsDTO dto);

	/**
	 * 获取复购用户数 (购买2次及以上)
	 * @param dto 统计参数
	 * @return Integer
	 */
	Integer getRepurchaseUserCount(OrderStatisticsDTO dto);

}