package com.aryn.cloud.order.service;

import com.aryn.cloud.order.api.vo.DailyReconcileVO;

import java.time.LocalDate;

/**
 * 用户日订单汇总任务与对账。
 */
public interface IDailyUserOrderStatService {

	int aggregateDay(String tenantId, LocalDate date);

	DailyReconcileVO reconcile(LocalDate startDate, LocalDate endDate);

}
