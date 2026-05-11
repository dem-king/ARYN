package com.aryn.cloud.order.controller.admin;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.order.api.dto.OrderStatisticsDTO;
import com.aryn.cloud.order.api.vo.*;
import com.aryn.cloud.order.service.IOrderStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/order/statistics")
@Tag(description = "orderstatistics", name = "订单统计")
public class OrderStatisticsController {

	private final IOrderStatisticsService orderStatisticsService;

	@GetMapping("/trade")
	@Operation(summary = "统计订单交易数据")
	public Result<OrderTradeStatisticsVO> getOrderTradeStatistics(OrderStatisticsDTO dto) {
		return Result.success(orderStatisticsService.getOrderTradeStatistics(dto));
	}

	@GetMapping("/trade/trend")
	@Operation(summary = "交易趋势统计")
	public Result<List<OrderTrendVO>> getOrderTradeTrend(OrderStatisticsDTO dto) {
		return Result.success(orderStatisticsService.getOrderTradeTrend(dto));
	}

	@GetMapping("/refund/rate/top10")
	@Operation(summary = "退款率Top10")
	public Result<List<OrderRefundRateVO>> getRefundRateTop10(OrderStatisticsDTO dto) {
		return Result.success(orderStatisticsService.getRefundRateTop10(dto));
	}

	@GetMapping("/user")
	@Operation(summary = "订单用户统计 (成交用户数、复购率、老客成交占比)")
	public Result<OrderUserStatisticsVO> getUserStatistics(OrderStatisticsDTO dto) {
		return Result.success(orderStatisticsService.getUserStatistics(dto));
	}

	@GetMapping("/overview")
	@Operation(summary = "订单概览统计 (售后、退款、超时未发货、负面评价)")
	public Result<OrderOverviewVO> getOrderOverview(OrderStatisticsDTO dto) {
		return Result.success(orderStatisticsService.getOrderOverview(dto));
	}

	@GetMapping("/product/sales/top10")
	@Operation(summary = "商品销量TOP10")
	public Result<List<OrderProductSalesRankVO>> getProductSalesTop10(OrderStatisticsDTO dto) {
		return Result.success(orderStatisticsService.getProductSalesTop10(dto));
	}

	@GetMapping("/status/overview")
	@Operation(summary = "订单状态总览 (待付款、待发货、已发货、已完成、售后中、已退款)")
	public Result<OrderStatusOverviewVO> getOrderStatusOverview(OrderStatisticsDTO dto) {
		return Result.success(orderStatisticsService.getOrderStatusOverview(dto));
	}

	@GetMapping("/user/consumption/frequency")
	@Operation(summary = "用户消费次数分布统计 (1次、2-3次、4-5次、6次以上)")
	public Result<UserConsumptionFrequencyVO> getUserConsumptionFrequency(OrderStatisticsDTO dto) {
		return Result.success(orderStatisticsService.getUserConsumptionFrequency(dto));
	}

	@GetMapping("/user/consumption/amount")
	@Operation(summary = "用户消费金额分层统计 (0-100、100-500、500-2000、2000以上)")
	public Result<UserConsumptionAmountVO> getUserConsumptionAmount(OrderStatisticsDTO dto) {
		return Result.success(orderStatisticsService.getUserConsumptionAmount(dto));
	}

	@GetMapping("/user/consumption/analysis")
	@Operation(summary = "用户消费分析 (成交用户数、总消费金额、人均消费、客单价、总订单数)")
	public Result<UserConsumptionAnalysisVO> getUserConsumptionAnalysis(OrderStatisticsDTO dto) {
		return Result.success(orderStatisticsService.getUserConsumptionAnalysis(dto));
	}

	@GetMapping("/user/transacting/count")
	@Operation(summary = "累计成交用户数")
	public Result<Long> getAccumulatedTransactionUserCount(OrderStatisticsDTO dto) {
		return Result.success(orderStatisticsService.getAccumulatedTransactionUserCount(dto));
	}

	@GetMapping("/user/growth/trend")
	@Operation(summary = "用户增长趋势 (新增用户、成交用户)")
	public Result<List<UserGrowthTrendVO>> getUserGrowthTrend(OrderStatisticsDTO dto) {
		return Result.success(orderStatisticsService.getUserGrowthTrend(dto));
	}

	@GetMapping("/product/sales/analysis")
	@Operation(summary = "商品销售能力分析 (动销商品数、销售件数、销售额、客单价、动销率)")
	public Result<ProductSalesAnalysisVO> getProductSalesAnalysis(OrderStatisticsDTO dto) {
		return Result.success(orderStatisticsService.getProductSalesAnalysis(dto));
	}

}
