package com.aryn.cloud.order.mapper;

import com.aryn.cloud.order.api.dto.OrderStatisticsDTO;
import com.aryn.cloud.order.api.vo.CategoryRankVO;
import com.aryn.cloud.order.api.vo.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 订单统计 Mapper
 */
@Mapper
public interface OrderStatisticsMapper {

	/**
	 * 统计订单交易数据：成交额、支付订单数、支付买家数、客单价、售后订单数、退款金额
	 * @param dto 统计参数
	 * @return OrderTradeStatisticsVO
	 */
	OrderTradeStatisticsVO getOrderTradeStatistics(@Param("dto") OrderStatisticsDTO dto);

	/**
	 * 交易趋势统计
	 * @param dto 统计参数
	 * @param format 时间格式 (MySQL DATE_FORMAT pattern)
	 * @return List<OrderTrendVO>
	 */
	List<OrderTrendVO> getOrderTradeTrend(@Param("dto") OrderStatisticsDTO dto, @Param("format") String format);

	/**
	 * 退款率 TOP 10
	 * @param dto 统计参数
	 * @return List<OrderRefundRateVO>
	 */
	List<OrderRefundRateVO> getRefundRateTop10(@Param("dto") OrderStatisticsDTO dto);

	/**
	 * 查询复购用户数量 (购买2次及以上)
	 * @param dto 统计参数
	 * @return Integer
	 */
	Integer getRepurchaseUserCount(@Param("dto") OrderStatisticsDTO dto);

	/**
	 * 查询老客成交金额 (在此之前有过支付订单的用户)
	 * @param dto 统计参数
	 * @return BigDecimal
	 */
	BigDecimal getOldCustomerGmv(@Param("dto") OrderStatisticsDTO dto);

	/**
	 * 订单概览统计 (售后、退款、超时未发货)
	 * @param dto 统计参数
	 * @return OrderOverviewVO
	 */
	OrderOverviewVO getOrderOverview(@Param("dto") OrderStatisticsDTO dto);

	/**
	 * 商品销量排行 TOP 10
	 * @param dto 统计参数
	 * @return List<OrderProductSalesRankVO>
	 */
	List<OrderProductSalesRankVO> getProductSalesTop10(@Param("dto") OrderStatisticsDTO dto);

	/**
	 * 订单状态总览
	 * @param dto 统计参数
	 * @return OrderStatusOverviewVO
	 */
	OrderStatusOverviewVO getOrderStatusOverview(@Param("dto") OrderStatisticsDTO dto);

	/**
	 * 用户消费次数分布统计
	 * @param dto 统计参数
	 * @return UserConsumptionFrequencyVO
	 */
	UserConsumptionFrequencyVO getUserConsumptionFrequency(@Param("dto") OrderStatisticsDTO dto);

	/**
	 * 用户消费金额分层统计
	 * @param dto 统计参数
	 * @return UserConsumptionAmountVO
	 */
	UserConsumptionAmountVO getUserConsumptionAmount(@Param("dto") OrderStatisticsDTO dto);

	/**
	 * 获取累计成交用户数
	 * @param dto 统计参数
	 * @return Long
	 */
	Long getAccumulatedTransactionUserCount(@Param("dto") OrderStatisticsDTO dto);

	/**
	 * 商品销售能力分析
	 * @param dto 统计参数
	 * @return ProductSalesAnalysisVO
	 */
	ProductSalesAnalysisVO getProductSalesAnalysis(@Param("dto") OrderStatisticsDTO dto);

	/**
	 * 品类销售排行 Top10
	 * @param dto 统计参数
	 * @return List<CategoryRankVO>
	 */
	List<CategoryRankVO> getCategoryRank(@Param("dto") OrderStatisticsDTO dto);

}
