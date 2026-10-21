package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.order.api.vo.DailyReconcileRawVO;
import com.aryn.cloud.order.api.vo.DailyReconcileVO;
import com.aryn.cloud.order.mapper.DailyUserOrderStatMapper;
import com.aryn.cloud.order.mapper.OrderStatisticsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyUserOrderStatServiceImplTest {

	@Mock
	private DailyUserOrderStatMapper dailyUserOrderStatMapper;

	@Mock
	private OrderStatisticsMapper orderStatisticsMapper;

	private DailyUserOrderStatServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new DailyUserOrderStatServiceImpl(dailyUserOrderStatMapper, orderStatisticsMapper);
	}

	@Test
	void aggregateDayDeletesThenAggregatesTheWholeDay() {
		LocalDate date = LocalDate.of(2026, 10, 20);
		when(dailyUserOrderStatMapper.aggregateDay(any(), any(), any(), any())).thenReturn(2);

		assertThat(service.aggregateDay("tenant-1", date)).isEqualTo(2);

		verify(dailyUserOrderStatMapper).deleteDay("tenant-1", date);
		verify(dailyUserOrderStatMapper).aggregateDay("tenant-1", date, date.atStartOfDay(),
				date.plusDays(1).atStartOfDay());
	}

	@Test
	void reconcileComputesRawMinusAggregateDiffs() {
		DailyReconcileRawVO raw = new DailyReconcileRawVO();
		raw.setGmv(new BigDecimal("1000.00"));
		raw.setOrderCount(5);
		raw.setBuyerCount(4);
		DailyReconcileRawVO aggregate = new DailyReconcileRawVO();
		aggregate.setGmv(new BigDecimal("900.00"));
		aggregate.setOrderCount(5);
		aggregate.setBuyerCount(4);
		when(orderStatisticsMapper.getRawDailySummary(any(), any())).thenReturn(raw);
		when(dailyUserOrderStatMapper.getAggregateSummary(any(), any())).thenReturn(aggregate);

		DailyReconcileVO result = service.reconcile(LocalDate.of(2026, 10, 1),
				LocalDate.of(2026, 10, 20));

		assertThat(result.getGmvDiff()).isEqualByComparingTo(new BigDecimal("100.00"));
		assertThat(result.getOrderDiff()).isZero();
		assertThat(result.getBuyerDiff()).isZero();
		assertThat(result.getStartDate()).isEqualTo("2026-10-01");
		assertThat(result.getEndDate()).isEqualTo("2026-10-20");
	}

}
