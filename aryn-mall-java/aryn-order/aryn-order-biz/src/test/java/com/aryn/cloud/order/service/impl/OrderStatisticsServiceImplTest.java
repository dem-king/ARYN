package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.order.api.dto.OrderStatisticsDTO;
import com.aryn.cloud.order.api.vo.ChannelContributionVO;
import com.aryn.cloud.order.api.vo.DailyReconcileRawVO;
import com.aryn.cloud.order.api.vo.OrderRefundSummaryVO;
import com.aryn.cloud.order.api.vo.ChannelUserStatVO;
import com.aryn.cloud.order.api.vo.OrderDashboardStatisticsVO;
import com.aryn.cloud.order.api.vo.OrderFunnelVO;
import com.aryn.cloud.order.api.vo.OrderOverviewVO;
import com.aryn.cloud.order.api.vo.OrderStatusOverviewVO;
import com.aryn.cloud.order.api.vo.OrderTradeStatisticsVO;
import com.aryn.cloud.order.api.vo.OrderTrendVO;
import com.aryn.cloud.order.api.vo.OrderUserStatisticsVO;
import com.aryn.cloud.order.api.vo.TrafficFunnelVO;
import com.aryn.cloud.order.api.vo.ProductSalesAnalysisVO;
import com.aryn.cloud.order.api.vo.UserCohortRetentionVO;
import com.aryn.cloud.order.api.vo.UserConsumptionAmountVO;
import com.aryn.cloud.order.api.vo.UserConsumptionFrequencyVO;
import com.aryn.cloud.order.api.vo.UserRfmSegmentVO;
import com.aryn.cloud.order.mapper.DailyUserOrderStatMapper;
import com.aryn.cloud.order.mapper.OrderStatisticsMapper;
import com.aryn.cloud.product.api.remote.RemoteGoodsAppraiseService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.aryn.cloud.product.api.vo.ProductOverviewVO;
import com.aryn.cloud.user.api.dto.UserStatisticsDTO;
import com.aryn.cloud.user.api.remote.RemoteUserStatisticsService;
import com.aryn.cloud.user.api.vo.UserSourceVO;
import com.aryn.cloud.user.api.vo.UserTrendVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OrderStatisticsServiceImplTest {

	@Mock
	private OrderStatisticsMapper orderStatisticsMapper;

	@Mock
	private DailyUserOrderStatMapper dailyUserOrderStatMapper;

	@Mock
	private RemoteGoodsAppraiseService remoteGoodsAppraiseService;

	@Mock
	private RemoteUserStatisticsService remoteUserStatisticsService;

	@Mock
	private RemoteGoodsSpuService remoteGoodsSpuService;

	private OrderStatisticsServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new OrderStatisticsServiceImpl(orderStatisticsMapper, dailyUserOrderStatMapper,
				remoteGoodsAppraiseService, remoteUserStatisticsService, remoteGoodsSpuService);
	}

	@Test
	void getUserStatisticsKeepsRatiosAsDecimalsAndAggregatesNewUsers() {
		OrderStatisticsDTO dto = buildDto();

		OrderTradeStatisticsVO trade = new OrderTradeStatisticsVO();
		trade.setGmv(new BigDecimal("1000.00"));
		trade.setPayOrderCount(5);
		trade.setPayBuyerCount(4);
		when(orderStatisticsMapper.getOrderTradeStatistics(dto)).thenReturn(trade);
		when(orderStatisticsMapper.getRepurchaseUserCount(dto)).thenReturn(2);
		when(orderStatisticsMapper.getOldCustomerGmv(dto)).thenReturn(new BigDecimal("400.00"));

		UserTrendVO firstDay = new UserTrendVO();
		firstDay.setNewUserCount(3);
		UserTrendVO secondDay = new UserTrendVO();
		secondDay.setNewUserCount(5);
		when(remoteUserStatisticsService.getUserTrend(any(UserStatisticsDTO.class)))
			.thenReturn(List.of(firstDay, secondDay));

		OrderUserStatisticsVO result = service.getUserStatistics(dto);

		assertThat(result.getPayBuyerCount()).isEqualTo(4);
		assertThat(result.getRepurchaseRate()).isEqualByComparingTo(new BigDecimal("0.5000"));
		assertThat(result.getOldCustomerGmvRate()).isEqualByComparingTo(new BigDecimal("0.4000"));
		assertThat(result.getNewUserCount()).isEqualTo(8);
	}

	@Test
	void getUserStatisticsSkipsNewUserLookupWithoutDateRange() {
		OrderStatisticsDTO dto = new OrderStatisticsDTO();
		OrderTradeStatisticsVO trade = new OrderTradeStatisticsVO();
		trade.setGmv(BigDecimal.ZERO);
		trade.setPayOrderCount(0);
		trade.setPayBuyerCount(0);
		when(orderStatisticsMapper.getOrderTradeStatistics(dto)).thenReturn(trade);

		OrderUserStatisticsVO result = service.getUserStatistics(dto);

		assertThat(result.getNewUserCount()).isZero();
		verifyNoInteractions(remoteUserStatisticsService);
	}

	@Test
	void getOrderDashboardStatisticsComposesOrderDataAndComparison() {
		OrderStatisticsDTO dto = buildDto();
		OrderStatisticsDTO compareDto = new OrderStatisticsDTO();
		compareDto.setStartTime(LocalDateTime.of(2026, 9, 1, 0, 0));
		compareDto.setEndTime(LocalDateTime.of(2026, 9, 30, 23, 59, 59));
		dto.setCompareStartTime(compareDto.getStartTime());
		dto.setCompareEndTime(compareDto.getEndTime());
		dto.setIncludeUserAnalytics(true);

		OrderTradeStatisticsVO trade = trade("1000.00", 5, 4);
		OrderTradeStatisticsVO compareTrade = trade("800.00", 4, 3);
		when(orderStatisticsMapper.getOrderTradeStatistics(dto)).thenReturn(trade);
		when(orderStatisticsMapper.getOrderTradeStatistics(compareDto)).thenReturn(compareTrade);

		OrderTrendVO trend = new OrderTrendVO();
		trend.setTimePoint("2026-10-20");
		trend.setGmv(new BigDecimal("1000.00"));
		trend.setPayOrderCount(5);
		trend.setPayBuyerCount(4);
		when(orderStatisticsMapper.getOrderTradeTrend(any(), anyString())).thenReturn(List.of(trend));

		when(orderStatisticsMapper.getRefundRateTop10(dto)).thenReturn(List.of());
		when(orderStatisticsMapper.getProductSalesTop10(dto)).thenReturn(List.of());
		when(orderStatisticsMapper.getOrderStatusOverview(dto)).thenReturn(new OrderStatusOverviewVO());
		when(orderStatisticsMapper.getOrderOverview(dto)).thenReturn(new OrderOverviewVO());
		OrderFunnelVO funnel = new OrderFunnelVO();
		funnel.setCreatedCount(10L);
		funnel.setPaidCount(8L);
		funnel.setDeliveredCount(6L);
		funnel.setCompletedCount(5L);
		funnel.setRefundedCount(1L);
		when(orderStatisticsMapper.getOrderFunnel(dto)).thenReturn(funnel);
		TrafficFunnelVO trafficFunnel = new TrafficFunnelVO();
		trafficFunnel.setCreatedBuyerCount(8L);
		trafficFunnel.setPaidBuyerCount(6L);
		when(orderStatisticsMapper.getBuyerFunnel(dto)).thenReturn(trafficFunnel);
		when(remoteGoodsSpuService.countVisitors(any(), any())).thenReturn(100L);
		UserRfmSegmentVO rfmSegment = new UserRfmSegmentVO();
		rfmSegment.setSegment("重要价值客户");
		rfmSegment.setUserCount(2L);
		rfmSegment.setGmv(new BigDecimal("1200.00"));
		when(orderStatisticsMapper.getUserRfmSegments(dto)).thenReturn(List.of(rfmSegment));
		UserCohortRetentionVO cohortRow = new UserCohortRetentionVO();
		cohortRow.setCohortMonth("2026-10");
		cohortRow.setCohortSize(10L);
		cohortRow.setMonth0(10L);
		cohortRow.setMonth1(4L);
		when(orderStatisticsMapper.getUserCohortRetention(dto)).thenReturn(List.of(cohortRow));
		when(orderStatisticsMapper.getRepurchaseUserCount(dto)).thenReturn(2);
		when(orderStatisticsMapper.getOldCustomerGmv(dto)).thenReturn(new BigDecimal("400.00"));
		when(orderStatisticsMapper.getUserConsumptionFrequency(dto)).thenReturn(new UserConsumptionFrequencyVO());
		when(orderStatisticsMapper.getUserConsumptionAmount(dto)).thenReturn(new UserConsumptionAmountVO());
		when(orderStatisticsMapper.getAccumulatedTransactionUserCount(dto)).thenReturn(3L);
		when(remoteGoodsAppraiseService.countNegativeAppraise(any(), any())).thenReturn(0L);

		UserTrendVO userTrend = new UserTrendVO();
		userTrend.setTimePoint("2026-10-20");
		userTrend.setNewUserCount(5);
		when(remoteUserStatisticsService.getUserTrend(any(UserStatisticsDTO.class))).thenReturn(List.of(userTrend));

		OrderDashboardStatisticsVO result = service.getOrderDashboardStatistics(dto);

		assertThat(result.getTrade()).isSameAs(trade);
		assertThat(result.getCompareTrade()).isSameAs(compareTrade);
		assertThat(result.getTradeTrend()).hasSize(20).contains(trend);
		assertThat(result.getUser().getNewUserCount()).isEqualTo(5);
		assertThat(result.getConsumption().getAverageOrderValue())
			.isEqualByComparingTo(new BigDecimal("200.00"));
		assertThat(result.getCompareConsumption().getAverageOrderValue())
			.isEqualByComparingTo(new BigDecimal("200.00"));
		assertThat(result.getUserGrowthTrend()).hasSize(20);
		assertThat(result.getUserGrowthTrend().get(19).getTimePoint()).isEqualTo("2026-10-20");
		assertThat(result.getUserGrowthTrend().get(19).getTransactingUserCount()).isEqualTo(4);
		assertThat(result.getUserGrowthTrend().get(19).getNewUserCount()).isEqualTo(5);
		assertThat(result.getAccumulatedTransactionUserCount()).isEqualTo(3L);
		assertThat(result.getFunnel().getCreatedCount()).isEqualTo(10L);
		assertThat(result.getFunnel().getRefundedCount()).isEqualTo(1L);
		assertThat(result.getRfmSegments()).singleElement().satisfies(segment -> {
			assertThat(segment.getSegment()).isEqualTo("重要价值客户");
			assertThat(segment.getUserCount()).isEqualTo(2L);
		});
		assertThat(result.getCohortRetention()).singleElement().satisfies(cohort -> {
			assertThat(cohort.getCohortMonth()).isEqualTo("2026-10");
			assertThat(cohort.getMonth1()).isEqualTo(4L);
		});
		assertThat(result.getTrafficFunnel().getVisitorCount()).isEqualTo(100L);
		assertThat(result.getTrafficFunnel().getCreatedBuyerCount()).isEqualTo(8L);
		assertThat(result.getTrafficFunnel().getPaidBuyerCount()).isEqualTo(6L);
	}

	@Test
	void getChannelContributionGroupsUsersBySource() {
		OrderStatisticsDTO dto = buildDto();
		ChannelUserStatVO first = channelUser("user-1", 2, "100.00");
		ChannelUserStatVO second = channelUser("user-2", 1, "50.00");
		ChannelUserStatVO third = channelUser("user-3", 1, "30.00");
		when(orderStatisticsMapper.getChannelUserStats(dto)).thenReturn(List.of(first, second, third));

		UserSourceVO firstSource = new UserSourceVO();
		firstSource.setUserId("user-1");
		firstSource.setSource("0");
		UserSourceVO secondSource = new UserSourceVO();
		secondSource.setUserId("user-2");
		secondSource.setSource("1");
		when(remoteUserStatisticsService.getUserSources(any())).thenReturn(List.of(firstSource, secondSource));

		List<ChannelContributionVO> result = service.getChannelContribution(dto);

		assertThat(result).hasSize(3);
		assertThat(result.get(0).getSourceName()).isEqualTo("微信小程序");
		assertThat(result.get(0).getGmv()).isEqualByComparingTo(new BigDecimal("100.00"));
		assertThat(result.get(0).getBuyerCount()).isEqualTo(1L);
		assertThat(result.get(0).getOrderCount()).isEqualTo(2L);
		assertThat(result.get(1).getSourceName()).isEqualTo("H5");
		assertThat(result.get(2).getSourceName()).isEqualTo("未知");
	}

	@Test
	void dailyStatSwitchUsesAggregateForCompleteHistoricalRange() {
		OrderStatisticsDTO dto = new OrderStatisticsDTO();
		dto.setStartTime(LocalDateTime.of(2026, 9, 1, 0, 0));
		dto.setEndTime(LocalDateTime.of(2026, 9, 10, 23, 59, 59));
		ReflectionTestUtils.setField(service, "dailyStatEnabled", true);

		LocalDate start = LocalDate.of(2026, 9, 1);
		LocalDate end = LocalDate.of(2026, 9, 10);
		when(dailyUserOrderStatMapper.countAggregateDays(start, end)).thenReturn(10L);
		DailyReconcileRawVO aggregate = new DailyReconcileRawVO();
		aggregate.setGmv(new BigDecimal("1000.00"));
		aggregate.setOrderCount(5);
		aggregate.setBuyerCount(4);
		when(dailyUserOrderStatMapper.getAggregateSummary(start, end)).thenReturn(aggregate);
		OrderRefundSummaryVO refund = new OrderRefundSummaryVO();
		refund.setAfterSalesOrderCount(1);
		refund.setRefundAmount(new BigDecimal("50.00"));
		when(orderStatisticsMapper.getRefundSummary(dto)).thenReturn(refund);

		OrderTradeStatisticsVO trade = service.getOrderTradeStatistics(dto);

		assertThat(trade.getGmv()).isEqualByComparingTo(new BigDecimal("1000.00"));
		assertThat(trade.getPayOrderCount()).isEqualTo(5);
		assertThat(trade.getPayBuyerCount()).isEqualTo(4);
		assertThat(trade.getAfterSalesOrderCount()).isEqualTo(1);
		assertThat(trade.getRefundAmount()).isEqualByComparingTo(new BigDecimal("50.00"));
		assertThat(trade.getAverageTicketSize()).isEqualByComparingTo(new BigDecimal("200.00"));

		OrderTrendVO firstDay = new OrderTrendVO();
		firstDay.setTimePoint("2026-09-01");
		firstDay.setGmv(new BigDecimal("500.00"));
		firstDay.setPayOrderCount(3);
		firstDay.setPayBuyerCount(2);
		when(dailyUserOrderStatMapper.getAggregateTrend(start, end)).thenReturn(List.of(firstDay));

		List<OrderTrendVO> trend = service.getOrderTradeTrend(dto);

		assertThat(trend).hasSize(10);
		assertThat(trend.get(0).getGmv()).isEqualByComparingTo(new BigDecimal("500.00"));
		assertThat(trend.get(9).getGmv()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	@Test
	void getProductSalesAnalysisKeepsRatesAsDecimals() {
		OrderStatisticsDTO dto = buildDto();

		ProductSalesAnalysisVO analysis = new ProductSalesAnalysisVO();
		analysis.setActiveProductCount(2);
		analysis.setSalesCount(6);
		analysis.setSalesAmount(new BigDecimal("1000.00"));
		analysis.setOrderCount(4);
		when(orderStatisticsMapper.getProductSalesAnalysis(dto)).thenReturn(analysis);

		ProductOverviewVO overview = new ProductOverviewVO();
		overview.setOnSaleCount(10);
		when(remoteGoodsSpuService.getProductOverview()).thenReturn(overview);

		ProductSalesAnalysisVO result = service.getProductSalesAnalysis(dto);

		assertThat(result.getAverageTicketSize()).isEqualByComparingTo(new BigDecimal("250.00"));
		assertThat(result.getActiveProductRate()).isEqualByComparingTo(new BigDecimal("0.2000"));
	}

	private OrderStatisticsDTO buildDto() {
		OrderStatisticsDTO dto = new OrderStatisticsDTO();
		dto.setStartTime(LocalDateTime.of(2026, 10, 1, 0, 0));
		dto.setEndTime(LocalDateTime.of(2026, 10, 20, 23, 59, 59));
		return dto;
	}

	private ChannelUserStatVO channelUser(String userId, int orderCount, String gmv) {
		ChannelUserStatVO stat = new ChannelUserStatVO();
		stat.setUserId(userId);
		stat.setOrderCount(orderCount);
		stat.setGmv(new BigDecimal(gmv));
		return stat;
	}

	private OrderTradeStatisticsVO trade(String gmv, int payOrderCount, int payBuyerCount) {
		OrderTradeStatisticsVO trade = new OrderTradeStatisticsVO();
		trade.setGmv(new BigDecimal(gmv));
		trade.setPayOrderCount(payOrderCount);
		trade.setPayBuyerCount(payBuyerCount);
		return trade;
	}

}
