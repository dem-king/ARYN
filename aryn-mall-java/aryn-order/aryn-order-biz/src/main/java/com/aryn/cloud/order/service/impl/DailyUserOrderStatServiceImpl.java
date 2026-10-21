package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.order.api.vo.DailyReconcileRawVO;
import com.aryn.cloud.order.api.vo.DailyReconcileVO;
import com.aryn.cloud.order.mapper.DailyUserOrderStatMapper;
import com.aryn.cloud.order.mapper.OrderStatisticsMapper;
import com.aryn.cloud.order.service.IDailyUserOrderStatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户日订单汇总任务与对账。
 */
@Service
@RequiredArgsConstructor
public class DailyUserOrderStatServiceImpl implements IDailyUserOrderStatService {

	private final DailyUserOrderStatMapper dailyUserOrderStatMapper;

	private final OrderStatisticsMapper orderStatisticsMapper;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int aggregateDay(String tenantId, LocalDate date) {
		LocalDateTime startTime = date.atStartOfDay();
		LocalDateTime endTime = date.plusDays(1).atStartOfDay();
		dailyUserOrderStatMapper.deleteDay(tenantId, date);
		return dailyUserOrderStatMapper.aggregateDay(tenantId, date, startTime, endTime);
	}

	@Override
	public DailyReconcileVO reconcile(LocalDate startDate, LocalDate endDate) {
		LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(1);
		LocalDate end = endDate != null ? endDate : start;
		if (start.isAfter(end)) {
			LocalDate temp = start;
			start = end;
			end = temp;
		}

		LocalDateTime startTime = start.atStartOfDay();
		LocalDateTime endTime = end.plusDays(1).atStartOfDay().minusSeconds(1);
		DailyReconcileRawVO raw = orderStatisticsMapper.getRawDailySummary(startTime, endTime);
		DailyReconcileRawVO aggregate = dailyUserOrderStatMapper.getAggregateSummary(start, end);

		BigDecimal rawGmv = raw != null ? safe(raw.getGmv()) : BigDecimal.ZERO;
		int rawOrders = raw != null ? safe(raw.getOrderCount()) : 0;
		int rawBuyers = raw != null ? safe(raw.getBuyerCount()) : 0;
		BigDecimal aggregateGmv = aggregate != null ? safe(aggregate.getGmv()) : BigDecimal.ZERO;
		int aggregateOrders = aggregate != null ? safe(aggregate.getOrderCount()) : 0;
		int aggregateBuyers = aggregate != null ? safe(aggregate.getBuyerCount()) : 0;

		DailyReconcileVO result = new DailyReconcileVO();
		result.setStartDate(start.toString());
		result.setEndDate(end.toString());
		result.setRawGmv(rawGmv);
		result.setRawPaidOrders(rawOrders);
		result.setRawPaidBuyers(rawBuyers);
		result.setAggregateGmv(aggregateGmv);
		result.setAggregatePaidOrders(aggregateOrders);
		result.setAggregatePaidBuyers(aggregateBuyers);
		result.setGmvDiff(rawGmv.subtract(aggregateGmv));
		result.setOrderDiff(rawOrders - aggregateOrders);
		result.setBuyerDiff(rawBuyers - aggregateBuyers);
		return result;
	}

	private BigDecimal safe(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private int safe(Integer value) {
		return value == null ? 0 : value;
	}

}
