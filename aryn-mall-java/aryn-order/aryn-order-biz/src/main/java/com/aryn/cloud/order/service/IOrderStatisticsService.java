package com.aryn.cloud.order.service;

import com.aryn.cloud.order.api.dto.OrderStatisticsDTO;
import com.aryn.cloud.order.api.vo.CategoryRankVO;
import com.aryn.cloud.order.api.vo.*;

import java.util.List;

public interface IOrderStatisticsService {

	/**
	 * 统计订单交易数据：成交额、支付订单数、支付买家数、客单价、售后订单数、退款金额
	 * @param dto 统计参数
	 * @return OrderTradeStatisticsVO
	 */
	OrderTradeStatisticsVO getOrderTradeStatistics(OrderStatisticsDTO dto);

	/**
	 * 交易趋势统计
	 * @param dto 统计参数
	 * @return List<OrderTrendVO>
	 */
	List<OrderTrendVO> getOrderTradeTrend(OrderStatisticsDTO dto);

	/**
	 * 退款率 TOP 10
	 * @param dto 统计参数
	 * @return List<OrderRefundRateVO>
	 */
	List<OrderRefundRateVO> getRefundRateTop10(OrderStatisticsDTO dto);

	/**
	 * 订单用户统计 (成交用户数、复购率、老客成交占比)
	 * @param dto 统计参数
	 * @return OrderUserStatisticsVO
	 */
	OrderUserStatisticsVO getUserStatistics(OrderStatisticsDTO dto);

	/**
	 * 订单概览统计 (售后、退款、超时未发货、负面评价)
	 * @param dto 统计参数
	 * @return OrderOverviewVO
	 */
	OrderOverviewVO getOrderOverview(OrderStatisticsDTO dto);

	/**
	 * 商品销量排行 TOP 10
	 * @param dto 统计参数
	 * @return List<OrderProductSalesRankVO>
	 */
	List<OrderProductSalesRankVO> getProductSalesTop10(OrderStatisticsDTO dto);

	/**
	 * 订单状态总览
	 * @param dto 统计参数
	 * @return OrderStatusOverviewVO
	 */
	OrderStatusOverviewVO getOrderStatusOverview(OrderStatisticsDTO dto);

	/**
	 * 用户消费次数分布统计
	 * @param dto 统计参数
	 * @return UserConsumptionFrequencyVO
	 */
	UserConsumptionFrequencyVO getUserConsumptionFrequency(OrderStatisticsDTO dto);

	/**
	 * 用户消费金额分层统计
	 * @param dto 统计参数
	 * @return UserConsumptionAmountVO
	 */
	UserConsumptionAmountVO getUserConsumptionAmount(OrderStatisticsDTO dto);

	/**
	 * 用户消费分析
	 * @param dto 统计参数
	 * @return UserConsumptionAnalysisVO
	 */
	UserConsumptionAnalysisVO getUserConsumptionAnalysis(OrderStatisticsDTO dto);

	/**
	 * 获取累计成交用户数
	 * @param dto 统计参数
	 * @return Long
	 */
	Long getAccumulatedTransactionUserCount(OrderStatisticsDTO dto);

	/**
	 * 用户增长趋势统计
	 * @param dto 统计参数
	 * @return List<UserGrowthTrendVO>
	 */
	List<UserGrowthTrendVO> getUserGrowthTrend(OrderStatisticsDTO dto);

	/**
	 * 商品销售能力分析
	 * @param dto 统计参数
	 * @return ProductSalesAnalysisVO
	 */
	ProductSalesAnalysisVO getProductSalesAnalysis(OrderStatisticsDTO dto);

	/**
	 * 品类销售排行 Top10
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
