package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.order.api.dto.OrderStatisticsDTO;
import com.aryn.cloud.order.api.vo.*;
import com.aryn.cloud.order.mapper.DailyUserOrderStatMapper;
import com.aryn.cloud.order.mapper.OrderStatisticsMapper;
import com.aryn.cloud.order.service.IOrderStatisticsService;
import com.aryn.cloud.product.api.remote.RemoteGoodsAppraiseService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.aryn.cloud.product.api.vo.ProductOverviewVO;
import com.aryn.cloud.user.api.dto.UserStatisticsDTO;
import com.aryn.cloud.user.api.remote.RemoteUserStatisticsService;
import com.aryn.cloud.user.api.vo.UserSourceVO;
import com.aryn.cloud.user.api.vo.UserTrendVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderStatisticsServiceImpl implements IOrderStatisticsService {

	private final OrderStatisticsMapper orderStatisticsMapper;

	private final DailyUserOrderStatMapper dailyUserOrderStatMapper;

	@DubboReference
	private final RemoteGoodsAppraiseService remoteGoodsAppraiseService;

	@DubboReference
	private final RemoteUserStatisticsService remoteUserStatisticsService;

	@DubboReference
	private final RemoteGoodsSpuService remoteGoodsSpuService;

	@Value("${hx.dashboard.daily-stat.enabled:false}")
	private boolean dailyStatEnabled;

	@Override
	public OrderDashboardStatisticsVO getOrderDashboardStatistics(OrderStatisticsDTO dto) {
		OrderDashboardStatisticsVO dashboard = new OrderDashboardStatisticsVO();

		OrderTradeStatisticsVO trade = loadTradeStatistics(dto);
		dashboard.setTrade(trade);

		List<OrderTrendVO> tradeTrend = loadTradeTrend(dto);
		dashboard.setTradeTrend(tradeTrend);
		dashboard.setRefundTop10(getRefundRateTop10(dto));
		dashboard.setSalesTop10(getProductSalesTop10(dto));
		dashboard.setStatus(getOrderStatusOverview(dto));
		dashboard.setOverview(getOrderOverview(dto));
		dashboard.setFunnel(orderStatisticsMapper.getOrderFunnel(dto));
		dashboard.setTrafficFunnel(buildTrafficFunnel(dto));

		List<UserTrendVO> userTrend = loadUserTrend(dto);
		int newUserCount = userTrend.stream()
			.mapToInt(item -> item.getNewUserCount() == null ? 0 : item.getNewUserCount())
			.sum();
		dashboard.setUser(buildUserStatistics(trade, dto, newUserCount));

		if (Boolean.TRUE.equals(dto.getIncludeUserAnalytics())) {
			dashboard.setConsumptionFrequency(getUserConsumptionFrequency(dto));
			dashboard.setConsumptionAmount(getUserConsumptionAmount(dto));
			dashboard.setAccumulatedTransactionUserCount(getAccumulatedTransactionUserCount(dto));
			dashboard.setConsumption(buildConsumptionAnalysis(trade));
			dashboard.setUserGrowthTrend(buildUserGrowthTrend(tradeTrend, userTrend));
			dashboard.setRfmSegments(orderStatisticsMapper.getUserRfmSegments(dto));
			dashboard.setCohortRetention(orderStatisticsMapper.getUserCohortRetention(dto));
		}

		if (dto.getCompareStartTime() != null && dto.getCompareEndTime() != null) {
			OrderStatisticsDTO compareDto = new OrderStatisticsDTO();
			compareDto.setStartTime(dto.getCompareStartTime());
			compareDto.setEndTime(dto.getCompareEndTime());
			OrderTradeStatisticsVO compareTrade = orderStatisticsMapper.getOrderTradeStatistics(compareDto);
			dashboard.setCompareTrade(compareTrade);
			dashboard.setCompareConsumption(buildConsumptionAnalysis(compareTrade));
		}

		return dashboard;
	}

	@Override
	public OrderTradeStatisticsVO getOrderTradeStatistics(OrderStatisticsDTO dto) {
		return loadTradeStatistics(dto);
	}

	@Override
	public List<OrderTrendVO> getOrderTradeTrend(OrderStatisticsDTO dto) {
		return loadTradeTrend(dto);
	}

	private List<OrderTrendVO> getOrderTradeTrendRaw(OrderStatisticsDTO dto) {
		LocalDateTime startTime = dto.getStartTime();
		LocalDateTime endTime = dto.getEndTime();
		String format;
		List<String> timePoints = new ArrayList<>();
		DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

		if (startTime.toLocalDate().equals(endTime.toLocalDate())) {
			// 同一天，按小时分组
			format = "%H";
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
		OrderTradeStatisticsVO tradeStats = loadTradeStatistics(dto);
		List<UserTrendVO> userTrend = loadUserTrend(dto);
		int newUserCount = userTrend.stream()
			.mapToInt(item -> item.getNewUserCount() == null ? 0 : item.getNewUserCount())
			.sum();
		return buildUserStatistics(tradeStats, dto, newUserCount);
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
		long negativeAppraisalCount = 0L;
		try {
			negativeAppraisalCount = remoteGoodsAppraiseService.countNegativeAppraise(dto.getStartTime(),
					dto.getEndTime());
		}
		catch (Exception ex) {
			// 商品服务不可用时降级为 0，避免拖垮订单侧聚合统计
			log.warn("统计负面评价失败，已降级为 0", ex);
		}
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
		return buildConsumptionAnalysis(loadTradeStatistics(dto));
	}

	@Override
	public Long getAccumulatedTransactionUserCount(OrderStatisticsDTO dto) {
		Long count = orderStatisticsMapper.getAccumulatedTransactionUserCount(dto);
		return count != null ? count : 0L;
	}

	@Override
	public List<UserGrowthTrendVO> getUserGrowthTrend(OrderStatisticsDTO dto) {
		return buildUserGrowthTrend(loadTradeTrend(dto), loadUserTrend(dto));
	}

	@Override
	public List<ChannelContributionVO> getChannelContribution(OrderStatisticsDTO dto) {
		List<ChannelUserStatVO> userStats = orderStatisticsMapper.getChannelUserStats(dto);
		if (userStats == null || userStats.isEmpty()) {
			return List.of();
		}

		List<String> userIds = userStats.stream()
			.map(ChannelUserStatVO::getUserId)
			.filter(id -> id != null && !id.isBlank())
			.distinct()
			.toList();
		Map<String, String> sourceByUser = loadUserSources(userIds);

		Map<String, ChannelContributionVO> contributions = new LinkedHashMap<>();
		for (ChannelUserStatVO stat : userStats) {
			String source = sourceByUser.getOrDefault(stat.getUserId(), "");
			ChannelContributionVO contribution = contributions.computeIfAbsent(source, key -> {
				ChannelContributionVO vo = new ChannelContributionVO();
				vo.setSource(key);
				vo.setSourceName(sourceName(key));
				vo.setBuyerCount(0L);
				vo.setOrderCount(0L);
				vo.setGmv(BigDecimal.ZERO);
				return vo;
			});
			contribution.setBuyerCount(contribution.getBuyerCount() + 1);
			contribution.setOrderCount(contribution.getOrderCount()
					+ (stat.getOrderCount() == null ? 0 : stat.getOrderCount()));
			contribution.setGmv(contribution.getGmv()
				.add(stat.getGmv() == null ? BigDecimal.ZERO : stat.getGmv()));
		}

		return contributions.values().stream()
			.sorted(Comparator.comparing(ChannelContributionVO::getGmv).reversed())
			.toList();
	}

	private TrafficFunnelVO buildTrafficFunnel(OrderStatisticsDTO dto) {
		TrafficFunnelVO funnel = orderStatisticsMapper.getBuyerFunnel(dto);
		if (funnel == null) {
			funnel = new TrafficFunnelVO();
		}
		try {
			Long visitors = remoteGoodsSpuService.countVisitors(dto.getStartTime(), dto.getEndTime());
			funnel.setVisitorCount(visitors != null ? visitors : 0L);
		}
		catch (Exception ex) {
			log.warn("统计商品访客失败，已降级为 0", ex);
			funnel.setVisitorCount(0L);
		}
		return funnel;
	}

	private Map<String, String> loadUserSources(List<String> userIds) {
		if (userIds.isEmpty()) {
			return Map.of();
		}
		Map<String, String> result = new HashMap<>();
		for (int index = 0; index < userIds.size(); index += 1000) {
			List<String> batch = userIds.subList(index, Math.min(index + 1000, userIds.size()));
			try {
				List<UserSourceVO> sources = remoteUserStatisticsService.getUserSources(batch);
				if (sources != null) {
					for (UserSourceVO source : sources) {
						if (source.getUserId() != null) {
							result.put(source.getUserId(), source.getSource());
						}
					}
				}
			}
			catch (Exception ex) {
				log.warn("批量获取用户来源失败，渠道归因降级为未知", ex);
			}
		}
		return result;
	}

	private String sourceName(String source) {
		if (source == null || source.isBlank()) {
			return "未知";
		}
		return switch (source) {
			case "0" -> "微信小程序";
			case "1" -> "H5";
			case "2" -> "APP";
			default -> source;
		};
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

		if (vo.getOrderCount() != null && vo.getOrderCount() > 0) {
			vo.setAverageTicketSize(
					vo.getSalesAmount().divide(new BigDecimal(vo.getOrderCount()), 2, RoundingMode.HALF_UP));
		}
		else {
			vo.setAverageTicketSize(BigDecimal.ZERO);
		}

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

	private List<UserTrendVO> loadUserTrend(OrderStatisticsDTO dto) {
		if (dto.getStartTime() == null || dto.getEndTime() == null) {
			return List.of();
		}
		UserStatisticsDTO userDto = new UserStatisticsDTO();
		userDto.setStartTime(dto.getStartTime());
		userDto.setEndTime(dto.getEndTime());
		try {
			List<UserTrendVO> userTrendList = remoteUserStatisticsService.getUserTrend(userDto);
			return userTrendList == null ? List.of() : userTrendList;
		}
		catch (Exception ex) {
			// 用户服务不可用时保留订单侧统计，仅新增用户相关指标降级为 0
			log.warn("统计新增用户趋势失败，已降级为空", ex);
			return List.of();
		}
	}

	private boolean canUseDailyStat(OrderStatisticsDTO dto) {
		if (!dailyStatEnabled || dto.getStartTime() == null || dto.getEndTime() == null) {
			return false;
		}
		LocalDate start = dto.getStartTime().toLocalDate();
		LocalDate end = dto.getEndTime().toLocalDate();
		// 单日保留小时粒度；含今天的区间保留实时明细
		if (start.equals(end) || !end.isBefore(LocalDate.now())) {
			return false;
		}
		long expectedDays = ChronoUnit.DAYS.between(start, end) + 1;
		Long coveredDays = dailyUserOrderStatMapper.countAggregateDays(start, end);
		return coveredDays != null && coveredDays == expectedDays;
	}

	private OrderTradeStatisticsVO loadTradeStatistics(OrderStatisticsDTO dto) {
		if (!canUseDailyStat(dto)) {
			return orderStatisticsMapper.getOrderTradeStatistics(dto);
		}

		LocalDate start = dto.getStartTime().toLocalDate();
		LocalDate end = dto.getEndTime().toLocalDate();
		DailyReconcileRawVO aggregate = dailyUserOrderStatMapper.getAggregateSummary(start, end);
		OrderRefundSummaryVO refund = orderStatisticsMapper.getRefundSummary(dto);

		BigDecimal gmv = aggregate != null && aggregate.getGmv() != null
				? aggregate.getGmv() : BigDecimal.ZERO;
		int payOrderCount = aggregate != null && aggregate.getOrderCount() != null
				? aggregate.getOrderCount() : 0;
		int payBuyerCount = aggregate != null && aggregate.getBuyerCount() != null
				? aggregate.getBuyerCount() : 0;

		OrderTradeStatisticsVO vo = new OrderTradeStatisticsVO();
		vo.setGmv(gmv);
		vo.setPayOrderCount(payOrderCount);
		vo.setPayBuyerCount(payBuyerCount);
		vo.setAfterSalesOrderCount(refund != null && refund.getAfterSalesOrderCount() != null
				? refund.getAfterSalesOrderCount() : 0);
		vo.setRefundAmount(refund != null && refund.getRefundAmount() != null
				? refund.getRefundAmount() : BigDecimal.ZERO);
		vo.setAverageTicketSize(payOrderCount > 0
				? gmv.divide(new BigDecimal(payOrderCount), 2, RoundingMode.HALF_UP)
				: BigDecimal.ZERO);
		return vo;
	}

	private List<OrderTrendVO> loadTradeTrend(OrderStatisticsDTO dto) {
		if (!canUseDailyStat(dto)) {
			return getOrderTradeTrendRaw(dto);
		}

		LocalDate start = dto.getStartTime().toLocalDate();
		LocalDate end = dto.getEndTime().toLocalDate();
		List<OrderTrendVO> rows = dailyUserOrderStatMapper.getAggregateTrend(start, end);
		Map<String, OrderTrendVO> rowMap = rows == null ? Map.of()
				: rows.stream()
					.collect(Collectors.toMap(OrderTrendVO::getTimePoint, item -> item, (v1, v2) -> v1));

		List<OrderTrendVO> result = new ArrayList<>();
		for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
			String timePoint = date.toString();
			OrderTrendVO vo = rowMap.get(timePoint);
			if (vo == null) {
				vo = new OrderTrendVO();
				vo.setTimePoint(timePoint);
				vo.setGmv(BigDecimal.ZERO);
				vo.setPayOrderCount(0);
				vo.setPayBuyerCount(0);
			}
			result.add(vo);
		}
		return result;
	}

	private OrderUserStatisticsVO buildUserStatistics(OrderTradeStatisticsVO tradeStats, OrderStatisticsDTO dto,
			int newUserCount) {
		OrderUserStatisticsVO vo = new OrderUserStatisticsVO();

		Integer payBuyerCount = tradeStats != null && tradeStats.getPayBuyerCount() != null
				? tradeStats.getPayBuyerCount() : 0;
		BigDecimal totalGmv = tradeStats != null && tradeStats.getGmv() != null
				? tradeStats.getGmv() : BigDecimal.ZERO;

		vo.setPayBuyerCount(payBuyerCount);
		vo.setTotalGmv(totalGmv);
		vo.setNewUserCount(newUserCount);

		if (payBuyerCount > 0) {
			Integer repurchaseUserCount = orderStatisticsMapper.getRepurchaseUserCount(dto);
			repurchaseUserCount = repurchaseUserCount != null ? repurchaseUserCount : 0;
			vo.setRepurchaseRate(new BigDecimal(repurchaseUserCount).divide(new BigDecimal(payBuyerCount), 4,
					RoundingMode.HALF_UP));
		}
		else {
			vo.setRepurchaseRate(BigDecimal.ZERO);
		}

		if (totalGmv.compareTo(BigDecimal.ZERO) > 0) {
			BigDecimal oldCustomerGmv = orderStatisticsMapper.getOldCustomerGmv(dto);
			oldCustomerGmv = oldCustomerGmv != null ? oldCustomerGmv : BigDecimal.ZERO;
			vo.setOldCustomerGmv(oldCustomerGmv);
			vo.setOldCustomerGmvRate(oldCustomerGmv.divide(totalGmv, 4, RoundingMode.HALF_UP));
		}
		else {
			vo.setOldCustomerGmv(BigDecimal.ZERO);
			vo.setOldCustomerGmvRate(BigDecimal.ZERO);
		}

		return vo;
	}

	private UserConsumptionAnalysisVO buildConsumptionAnalysis(OrderTradeStatisticsVO tradeStats) {
		UserConsumptionAnalysisVO vo = new UserConsumptionAnalysisVO();

		Integer payBuyerCount = tradeStats != null && tradeStats.getPayBuyerCount() != null
				? tradeStats.getPayBuyerCount() : 0;
		Integer payOrderCount = tradeStats != null && tradeStats.getPayOrderCount() != null
				? tradeStats.getPayOrderCount() : 0;
		BigDecimal totalGmv = tradeStats != null && tradeStats.getGmv() != null
				? tradeStats.getGmv() : BigDecimal.ZERO;

		vo.setTransactingUserCount(payBuyerCount);
		vo.setTotalOrderCount(payOrderCount);
		vo.setTotalConsumptionAmount(totalGmv);

		if (payBuyerCount > 0) {
			vo.setAverageConsumptionPerUser(totalGmv.divide(new BigDecimal(payBuyerCount), 2, RoundingMode.HALF_UP));
		}
		else {
			vo.setAverageConsumptionPerUser(BigDecimal.ZERO);
		}

		if (payOrderCount > 0) {
			vo.setAverageOrderValue(totalGmv.divide(new BigDecimal(payOrderCount), 2, RoundingMode.HALF_UP));
		}
		else {
			vo.setAverageOrderValue(BigDecimal.ZERO);
		}

		return vo;
	}

	private List<UserGrowthTrendVO> buildUserGrowthTrend(List<OrderTrendVO> tradeTrendList,
			List<UserTrendVO> userTrendList) {
		Map<String, Integer> tradeMap = tradeTrendList.stream()
			.collect(Collectors.toMap(OrderTrendVO::getTimePoint,
					item -> item.getPayBuyerCount() == null ? 0 : item.getPayBuyerCount(), (v1, v2) -> v1));
		Map<String, Integer> userMap = userTrendList.stream()
			.filter(item -> item.getTimePoint() != null)
			.collect(Collectors.toMap(UserTrendVO::getTimePoint,
					item -> item.getNewUserCount() == null ? 0 : item.getNewUserCount(), (v1, v2) -> v1));

		List<UserGrowthTrendVO> result = new ArrayList<>();
		for (OrderTrendVO item : tradeTrendList) {
			UserGrowthTrendVO vo = new UserGrowthTrendVO();
			vo.setTimePoint(item.getTimePoint());
			vo.setTransactingUserCount(tradeMap.getOrDefault(item.getTimePoint(), 0));
			vo.setNewUserCount(userMap.getOrDefault(item.getTimePoint(), 0));
			result.add(vo);
		}
		return result;
	}

}
