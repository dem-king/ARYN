package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.order.api.dto.OrderStatisticsDTO;
import com.aryn.cloud.order.api.vo.*;
import com.aryn.cloud.order.mapper.OrderStatisticsMapper;
import com.aryn.cloud.order.service.IOrderStatisticsService;
import com.aryn.cloud.product.api.remote.RemoteGoodsAppraiseService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.aryn.cloud.product.api.vo.ProductOverviewVO;
import com.aryn.cloud.user.api.dto.UserStatisticsDTO;
import com.aryn.cloud.user.api.remote.RemoteUserStatisticsService;
import com.aryn.cloud.user.api.vo.UserTrendVO;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OrderStatisticsServiceImpl implements IOrderStatisticsService {

	private final OrderStatisticsMapper orderStatisticsMapper;

	@DubboReference
	private final RemoteGoodsAppraiseService remoteGoodsAppraiseService;

	@DubboReference
	private final RemoteUserStatisticsService remoteUserStatisticsService;

	@DubboReference
	private final RemoteGoodsSpuService remoteGoodsSpuService;

	@Override
	public OrderTradeStatisticsVO getOrderTradeStatistics(OrderStatisticsDTO dto) {
		return orderStatisticsMapper.getOrderTradeStatistics(dto);
	}

	@Override
	public List<OrderTrendVO> getOrderTradeTrend(OrderStatisticsDTO dto) {
		LocalDateTime startTime = dto.getStartTime();
		LocalDateTime endTime = dto.getEndTime();
		String format;
		List<String> timePoints = new ArrayList<>();
		DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

		if (startTime.toLocalDate().equals(endTime.toLocalDate())) {
			// 同一天，按小时分组
			format = "%H";
			// 生成小时列表 00-23 (或者 startHour - endHour)
			int startHour = startTime.getHour();
			int endHour = endTime.getHour();
			for (int i = startHour; i <= endHour; i++) {
				timePoints.add(String.format("%02d", i));
			}
		}
		else {
			// 不同天，按日期分组
			format = "%Y-%m-%d";
			LocalDateTime temp = startTime;
			// 这里的循环需要注意，如果endTime是 2023-10-02 00:00:00，通常认为是包含10-02的
			// 按天遍历
			while (!temp.toLocalDate().isAfter(endTime.toLocalDate())) {
				timePoints.add(temp.format(dateFormatter));
				temp = temp.plusDays(1);
			}
		}

		List<OrderTrendVO> dbList = orderStatisticsMapper.getOrderTradeTrend(dto, format);
		Map<String, OrderTrendVO> dbMap = dbList.stream()
			.collect(Collectors.toMap(OrderTrendVO::getTimePoint, v -> v, (v1, v2) -> v1));

		List<OrderTrendVO> result = new ArrayList<>();
		for (String tp : timePoints) {
			OrderTrendVO vo = dbMap.get(tp);
			if (vo == null) {
				vo = new OrderTrendVO();
				vo.setTimePoint(tp);
				vo.setGmv(BigDecimal.ZERO);
				vo.setPayOrderCount(0);
				vo.setPayBuyerCount(0);
			}
			result.add(vo);
		}
		return result;
	}

	@Override
	public List<OrderRefundRateVO> getRefundRateTop10(OrderStatisticsDTO dto) {
		return orderStatisticsMapper.getRefundRateTop10(dto);
	}

	@Override
	public OrderUserStatisticsVO getUserStatistics(OrderStatisticsDTO dto) {
		OrderUserStatisticsVO vo = new OrderUserStatisticsVO();

		// 1. 获取基础交易数据（包含成交用户数 payBuyerCount 和 总成交金额 gmv）
		OrderTradeStatisticsVO tradeStats = orderStatisticsMapper.getOrderTradeStatistics(dto);
		Integer payBuyerCount = tradeStats != null && tradeStats.getPayBuyerCount() != null
				? tradeStats.getPayBuyerCount() : 0;
		BigDecimal totalGmv = tradeStats != null && tradeStats.getGmv() != null ? tradeStats.getGmv() : BigDecimal.ZERO;

		vo.setPayBuyerCount(payBuyerCount);
		vo.setTotalGmv(totalGmv);

		// 2. 计算复购率
		if (payBuyerCount > 0) {
			Integer repurchaseUserCount = orderStatisticsMapper.getRepurchaseUserCount(dto);
			repurchaseUserCount = repurchaseUserCount != null ? repurchaseUserCount : 0;
			BigDecimal repurchaseRate = new BigDecimal(repurchaseUserCount).divide(new BigDecimal(payBuyerCount), 4,
					RoundingMode.HALF_UP);
			vo.setRepurchaseRate(repurchaseRate);
		}
		else {
			vo.setRepurchaseRate(BigDecimal.ZERO);
		}

		// 3. 计算老客成交占比
		if (totalGmv.compareTo(BigDecimal.ZERO) > 0) {
			BigDecimal oldCustomerGmv = orderStatisticsMapper.getOldCustomerGmv(dto);
			oldCustomerGmv = oldCustomerGmv != null ? oldCustomerGmv : BigDecimal.ZERO;
			vo.setOldCustomerGmv(oldCustomerGmv);

			BigDecimal oldCustomerGmvRate = oldCustomerGmv.divide(totalGmv, 4, RoundingMode.HALF_UP);
			vo.setOldCustomerGmvRate(oldCustomerGmvRate);
		}
		else {
			vo.setOldCustomerGmv(BigDecimal.ZERO);
			vo.setOldCustomerGmvRate(BigDecimal.ZERO);
		}

		return vo;
	}

	@Override
	public OrderOverviewVO getOrderOverview(OrderStatisticsDTO dto) {
		OrderOverviewVO vo = orderStatisticsMapper.getOrderOverview(dto);
		if (vo == null) {
			vo = new OrderOverviewVO();
			vo.setAfterSalesCount(0L);
			vo.setRefundCompletedCount(0L);
			vo.setUnshippedTimeoutCount(0L);
		}
		// 4. 统计负面评价数量 (调用产品服务)
		long negativeAppraisalCount = remoteGoodsAppraiseService.countNegativeAppraise(dto.getStartTime(),
				dto.getEndTime());
		vo.setNegativeAppraisalCount(negativeAppraisalCount);
		return vo;
	}

	@Override
	public List<OrderProductSalesRankVO> getProductSalesTop10(OrderStatisticsDTO dto) {
		return orderStatisticsMapper.getProductSalesTop10(dto);
	}

	@Override
	public OrderStatusOverviewVO getOrderStatusOverview(OrderStatisticsDTO dto) {
		return orderStatisticsMapper.getOrderStatusOverview(dto);
	}

	@Override
	public UserConsumptionFrequencyVO getUserConsumptionFrequency(OrderStatisticsDTO dto) {
		return orderStatisticsMapper.getUserConsumptionFrequency(dto);
	}

	@Override
	public UserConsumptionAmountVO getUserConsumptionAmount(OrderStatisticsDTO dto) {
		return orderStatisticsMapper.getUserConsumptionAmount(dto);
	}

	@Override
	public UserConsumptionAnalysisVO getUserConsumptionAnalysis(OrderStatisticsDTO dto) {
		UserConsumptionAnalysisVO vo = new UserConsumptionAnalysisVO();

		// 1. 获取基础交易数据
		OrderTradeStatisticsVO tradeStats = orderStatisticsMapper.getOrderTradeStatistics(dto);
		Integer payBuyerCount = tradeStats != null && tradeStats.getPayBuyerCount() != null
				? tradeStats.getPayBuyerCount() : 0;
		Integer payOrderCount = tradeStats != null && tradeStats.getPayOrderCount() != null
				? tradeStats.getPayOrderCount() : 0;
		BigDecimal totalGmv = tradeStats != null && tradeStats.getGmv() != null ? tradeStats.getGmv() : BigDecimal.ZERO;

		vo.setTransactingUserCount(payBuyerCount);
		vo.setTotalOrderCount(payOrderCount);
		vo.setTotalConsumptionAmount(totalGmv);

		// 2. 计算人均消费 (GMV / 成交用户数)
		if (payBuyerCount > 0) {
			vo.setAverageConsumptionPerUser(totalGmv.divide(new BigDecimal(payBuyerCount), 2, RoundingMode.HALF_UP));
		}
		else {
			vo.setAverageConsumptionPerUser(BigDecimal.ZERO);
		}

		// 3. 计算客单价 (GMV / 总订单数)
		if (payOrderCount > 0) {
			vo.setAverageOrderValue(totalGmv.divide(new BigDecimal(payOrderCount), 2, RoundingMode.HALF_UP));
		}
		else {
			vo.setAverageOrderValue(BigDecimal.ZERO);
		}

		return vo;
	}

	@Override
	public Long getAccumulatedTransactionUserCount(OrderStatisticsDTO dto) {
		Long count = orderStatisticsMapper.getAccumulatedTransactionUserCount(dto);
		return count != null ? count : 0L;
	}

	@Override
	public List<UserGrowthTrendVO> getUserGrowthTrend(OrderStatisticsDTO dto) {
		LocalDateTime startTime = dto.getStartTime();
		LocalDateTime endTime = dto.getEndTime();
		String format;
		List<String> timePoints = new ArrayList<>();
		DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

		if (startTime.toLocalDate().equals(endTime.toLocalDate())) {
			// 同一天，按小时分组
			format = "%H";
			// 生成小时列表 00-23 (或者 startHour - endHour)
			int startHour = startTime.getHour();
			int endHour = endTime.getHour();
			for (int i = startHour; i <= endHour; i++) {
				timePoints.add(String.format("%02d", i));
			}
		}
		else {
			// 不同天，按日期分组
			format = "%Y-%m-%d";
			LocalDateTime temp = startTime;
			// 按天遍历
			while (!temp.toLocalDate().isAfter(endTime.toLocalDate())) {
				timePoints.add(temp.format(dateFormatter));
				temp = temp.plusDays(1);
			}
		}

		// 1. 获取成交用户趋势 (从订单服务)
		List<OrderTrendVO> tradeTrendList = orderStatisticsMapper.getOrderTradeTrend(dto, format);
		Map<String, Integer> tradeMap = tradeTrendList.stream()
			.collect(Collectors.toMap(OrderTrendVO::getTimePoint, OrderTrendVO::getPayBuyerCount, (v1, v2) -> v1));

		// 2. 获取新增用户趋势 (远程调用用户服务)
		UserStatisticsDTO userDto = new UserStatisticsDTO();
		userDto.setStartTime(dto.getStartTime());
		userDto.setEndTime(dto.getEndTime());
		List<UserTrendVO> userTrendList = remoteUserStatisticsService.getUserTrend(userDto);
		if (userTrendList == null) {
			userTrendList = new ArrayList<>();
		}
		Map<String, Integer> userMap = userTrendList.stream()
			.collect(Collectors.toMap(UserTrendVO::getTimePoint, UserTrendVO::getNewUserCount, (v1, v2) -> v1));

		// 3. 合并数据
		List<UserGrowthTrendVO> result = new ArrayList<>();
		for (String tp : timePoints) {
			UserGrowthTrendVO vo = new UserGrowthTrendVO();
			vo.setTimePoint(tp);
			vo.setTransactingUserCount(tradeMap.getOrDefault(tp, 0));
			vo.setNewUserCount(userMap.getOrDefault(tp, 0));
			result.add(vo);
		}

		return result;
	}

	@Override
	public ProductSalesAnalysisVO getProductSalesAnalysis(OrderStatisticsDTO dto) {
		ProductSalesAnalysisVO vo = orderStatisticsMapper.getProductSalesAnalysis(dto);
		if (vo == null) {
			vo = new ProductSalesAnalysisVO();
			vo.setActiveProductCount(0);
			vo.setSalesCount(0);
			vo.setSalesAmount(BigDecimal.ZERO);
			vo.setOrderCount(0);
		}

		// 1. 计算客单价 (销售额 / 订单数)
		if (vo.getOrderCount() != null && vo.getOrderCount() > 0) {
			vo.setAverageTicketSize(
					vo.getSalesAmount().divide(new BigDecimal(vo.getOrderCount()), 2, RoundingMode.HALF_UP));
		}
		else {
			vo.setAverageTicketSize(BigDecimal.ZERO);
		}

		// 2. 计算动销率 (动销商品数 / 在售商品数)
		// 远程调用商品服务获取在售商品数
		ProductOverviewVO productOverview = remoteGoodsSpuService.getProductOverview();
		Integer onSaleCount = productOverview != null && productOverview.getOnSaleCount() != null
				? productOverview.getOnSaleCount() : 0;

		if (onSaleCount > 0 && vo.getActiveProductCount() != null) {
			BigDecimal activeProductRate = new BigDecimal(vo.getActiveProductCount())
				.divide(new BigDecimal(onSaleCount), 4, RoundingMode.HALF_UP);
			vo.setActiveProductRate(activeProductRate);
		}
		else {
			vo.setActiveProductRate(BigDecimal.ZERO);
		}

		return vo;
	}

}
