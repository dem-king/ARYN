package com.aryn.cloud.order.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 订单侧概览聚合数据，一次请求返回交易、趋势、状态、消费与增长等统计。
 */
@Data
@Schema(description = "订单侧概览聚合数据")
public class OrderDashboardStatisticsVO {

	@Schema(description = "交易指标")
	private OrderTradeStatisticsVO trade;

	@Schema(description = "对比期交易指标")
	private OrderTradeStatisticsVO compareTrade;

	@Schema(description = "交易趋势")
	private List<OrderTrendVO> tradeTrend;

	@Schema(description = "退款率 TOP 10")
	private List<OrderRefundRateVO> refundTop10;

	@Schema(description = "商品销量 TOP 10")
	private List<OrderProductSalesRankVO> salesTop10;

	@Schema(description = "订单用户统计")
	private OrderUserStatisticsVO user;

	@Schema(description = "服务与风控")
	private OrderOverviewVO overview;

	@Schema(description = "订单状态总览")
	private OrderStatusOverviewVO status;

	@Schema(description = "订单履约漏斗")
	private OrderFunnelVO funnel;

	@Schema(description = "用户 RFM 分层")
	private List<UserRfmSegmentVO> rfmSegments;

	@Schema(description = "用户首购 Cohort 留存")
	private List<UserCohortRetentionVO> cohortRetention;

	@Schema(description = "访问转化漏斗")
	private TrafficFunnelVO trafficFunnel;

	@Schema(description = "用户消费分析")
	private UserConsumptionAnalysisVO consumption;

	@Schema(description = "对比期用户消费分析")
	private UserConsumptionAnalysisVO compareConsumption;

	@Schema(description = "用户消费次数分布")
	private UserConsumptionFrequencyVO consumptionFrequency;

	@Schema(description = "用户消费金额分层")
	private UserConsumptionAmountVO consumptionAmount;

	@Schema(description = "用户增长趋势")
	private List<UserGrowthTrendVO> userGrowthTrend;

	@Schema(description = "累计成交用户数")
	private Long accumulatedTransactionUserCount;

}
