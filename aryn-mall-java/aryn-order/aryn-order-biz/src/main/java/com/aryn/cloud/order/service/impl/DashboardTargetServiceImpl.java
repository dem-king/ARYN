package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.order.api.dto.DashboardTargetDTO;
import com.aryn.cloud.order.api.entity.DashboardTarget;
import com.aryn.cloud.order.api.vo.DashboardMonthlyActualVO;
import com.aryn.cloud.order.api.vo.DashboardTargetVO;
import com.aryn.cloud.order.mapper.DashboardTargetMapper;
import com.aryn.cloud.order.mapper.OrderStatisticsMapper;
import com.aryn.cloud.order.service.IDashboardTargetService;
import com.aryn.cloud.user.api.dto.UserStatisticsDTO;
import com.aryn.cloud.user.api.remote.RemoteUserStatisticsService;
import com.aryn.cloud.user.api.vo.UserTrendVO;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * 管理端概览月度目标。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardTargetServiceImpl extends ServiceImpl<DashboardTargetMapper, DashboardTarget>
		implements IDashboardTargetService {

	private final OrderStatisticsMapper orderStatisticsMapper;

	@DubboReference
	private final RemoteUserStatisticsService remoteUserStatisticsService;

	@Override
	public DashboardTargetVO getTargetOverview(String month) {
		String targetMonth = normalizeMonth(month);
		YearMonth yearMonth = YearMonth.parse(targetMonth);
		LocalDateTime startTime = yearMonth.atDay(1).atStartOfDay();
		LocalDateTime endTime = yearMonth.atEndOfMonth().atTime(23, 59, 59);
		LocalDateTime now = LocalDateTime.now();
		if (endTime.isAfter(now)) {
			endTime = now;
		}

		DashboardTarget target = this.getOne(Wrappers.<DashboardTarget>lambdaQuery()
			.eq(DashboardTarget::getTargetMonth, targetMonth)
			.last("limit 1"));
		DashboardMonthlyActualVO actual = orderStatisticsMapper.getMonthlyActual(startTime, endTime);

		DashboardTargetVO vo = new DashboardTargetVO();
		vo.setTargetMonth(targetMonth);
		vo.setGmvTarget(target != null ? safe(target.getGmvTarget()) : BigDecimal.ZERO);
		vo.setOrderTarget(target != null ? safe(target.getOrderTarget()) : 0);
		vo.setNewUserTarget(target != null ? safe(target.getNewUserTarget()) : 0);
		vo.setGmvActual(actual != null ? safe(actual.getGmv()) : BigDecimal.ZERO);
		vo.setOrderActual(actual != null ? safe(actual.getOrderCount()) : 0);
		vo.setNewUserActual(loadNewUsers(startTime, endTime));
		return vo;
	}

	@Override
	public DashboardTargetVO saveTarget(DashboardTargetDTO dto) {
		String targetMonth = normalizeMonth(dto.getTargetMonth());
		DashboardTarget target = this.getOne(Wrappers.<DashboardTarget>lambdaQuery()
			.eq(DashboardTarget::getTargetMonth, targetMonth)
			.last("limit 1"));
		if (target == null) {
			target = new DashboardTarget();
		}
		target.setTargetMonth(targetMonth);
		target.setGmvTarget(nonNegative(dto.getGmvTarget()));
		target.setOrderTarget(nonNegative(dto.getOrderTarget()));
		target.setNewUserTarget(nonNegative(dto.getNewUserTarget()));
		this.saveOrUpdate(target);
		return getTargetOverview(targetMonth);
	}

	private int loadNewUsers(LocalDateTime startTime, LocalDateTime endTime) {
		try {
			UserStatisticsDTO dto = new UserStatisticsDTO();
			dto.setStartTime(startTime);
			dto.setEndTime(endTime);
			List<UserTrendVO> trend = remoteUserStatisticsService.getUserTrend(dto);
			return trend == null ? 0
					: trend.stream()
						.mapToInt(item -> item.getNewUserCount() == null ? 0 : item.getNewUserCount())
						.sum();
		}
		catch (Exception ex) {
			log.warn("统计月度新增用户失败，已降级为 0", ex);
			return 0;
		}
	}

	private String normalizeMonth(String month) {
		if (StringUtils.hasText(month)) {
			try {
				return YearMonth.parse(month).toString();
			}
			catch (DateTimeParseException ignored) {
				// 非法月份回退到当前月
			}
		}
		return YearMonth.now().toString();
	}

	private BigDecimal safe(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private Integer safe(Integer value) {
		return value == null ? 0 : value;
	}

	private BigDecimal nonNegative(BigDecimal value) {
		return value == null || value.signum() < 0 ? BigDecimal.ZERO : value;
	}

	private Integer nonNegative(Integer value) {
		return value == null || value < 0 ? 0 : value;
	}

}
