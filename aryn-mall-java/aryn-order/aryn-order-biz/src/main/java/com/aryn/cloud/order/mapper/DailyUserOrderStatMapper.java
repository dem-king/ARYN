package com.aryn.cloud.order.mapper;

import com.aryn.cloud.order.api.vo.DailyReconcileRawVO;
import com.aryn.cloud.order.api.vo.OrderTrendVO;
import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户日订单汇总任务与对账查询。
 */
@Mapper
public interface DailyUserOrderStatMapper {

	@InterceptorIgnore(tenantLine = "true")
	int deleteDay(@Param("tenantId") String tenantId, @Param("statDate") LocalDate statDate);

	@InterceptorIgnore(tenantLine = "true")
	int aggregateDay(@Param("tenantId") String tenantId, @Param("statDate") LocalDate statDate,
			@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

	DailyReconcileRawVO getAggregateSummary(@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	Long countAggregateDays(@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

	List<OrderTrendVO> getAggregateTrend(@Param("startDate") LocalDate startDate,
			@Param("endDate") LocalDate endDate);

}
