package com.aryn.cloud.promotion.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.entity.PromotionLock;
import com.aryn.cloud.promotion.api.vo.FirstOrderReportRowVO;
import com.aryn.cloud.promotion.api.vo.FirstOrderReportVO;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.mapper.PromotionLockMapper;
import com.aryn.cloud.promotion.service.impl.PromotionLockUsage;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 首单核销报表：按活动 × 主体类型统计锁定/核销/释放与让利，并给出月预算预警。
 *
 * <p>P2 只读报表，不改变营销锁状态；统计口径与 {@link PromotionLockUsage} 一致：
 * 锁定+确认占预算，释放不占预算。
 *
 * @author aryn
 * @since 2026/11/13
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FirstOrderReportService {

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	private final PromotionLockMapper promotionLockMapper;

	private final PromotionActivityMapper promotionActivityMapper;

	private final PromotionLockUsage promotionLockUsage;

	public FirstOrderReportVO report(String tenantId, LocalDateTime begin, LocalDateTime end) {
		LocalDateTime beginTime = begin == null ? LocalDateTime.now().minusDays(30) : begin;
		LocalDateTime endTime = end == null ? LocalDateTime.now() : end;

		QueryWrapper<PromotionLock> wrapper = new QueryWrapper<>();
		wrapper.select(
				"activity_id AS activity_id",
				"MAX(activity_name) AS activity_name",
				"subject_type AS subject_type",
				"COUNT(1) AS total_count",
				"SUM(CASE WHEN status = '1' THEN 1 ELSE 0 END) AS locked_count",
				"SUM(CASE WHEN status = '2' THEN 1 ELSE 0 END) AS confirmed_count",
				"SUM(CASE WHEN status = '3' THEN 1 ELSE 0 END) AS released_count",
				"COUNT(DISTINCT subject_id) AS subject_count",
				"COALESCE(SUM(CASE WHEN status = '2' THEN discount_amount ELSE 0 END), 0) AS discount_amount")
			.eq("tenant_id", tenantId)
			.eq("activity_type", PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT)
			.isNotNull("subject_id")
			.eq("del_flag", "0")
			.between("create_time", beginTime, endTime)
			.groupBy("activity_id", "subject_type");

		List<Map<String, Object>> maps = promotionLockMapper.selectMaps(wrapper);
		Map<String, PromotionActivity> activities = loadActivities(tenantId, maps);
		LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0)
			.withNano(0);

		List<FirstOrderReportRowVO> rows = new ArrayList<>();
		for (Map<String, Object> map : maps) {
			String activityId = text(map, "activity_id");
			String subjectType = text(map, "subject_type");
			FirstOrderReportRowVO row = new FirstOrderReportRowVO();
			row.setActivityId(activityId);
			PromotionActivity activity = activities.get(activityId);
			row.setActivityName(activity != null && StringUtils.hasText(activity.getActivityName())
				? activity.getActivityName() : text(map, "activity_name"));
			row.setSubjectType(subjectType);
			row.setLockedCount(number(map, "locked_count"));
			row.setConfirmedCount(number(map, "confirmed_count"));
			row.setReleasedCount(number(map, "released_count"));
			row.setSubjectCount(number(map, "subject_count"));
			row.setDiscountAmount(decimal(map, "discount_amount"));
			BigDecimal budget = activity == null ? BigDecimal.ZERO : monthlyBudget(activity.getRules());
			BigDecimal used = activityId == null ? BigDecimal.ZERO
				: promotionLockUsage.sumDiscountSince(tenantId, activityId, monthStart);
			row.setMonthlyBudget(budget);
			row.setMonthlyUsed(used);
			if (budget.compareTo(BigDecimal.ZERO) > 0) {
				BigDecimal rate = used.multiply(BigDecimal.valueOf(100))
					.divide(budget, 2, RoundingMode.HALF_UP);
				row.setUsageRate(rate);
				row.setAlert(rate.compareTo(BigDecimal.valueOf(80)) >= 0);
			}
			else {
				row.setAlert(Boolean.FALSE);
			}
			rows.add(row);
		}

		FirstOrderReportVO report = new FirstOrderReportVO();
		report.setBeginTime(TIME_FORMAT.format(beginTime));
		report.setEndTime(TIME_FORMAT.format(endTime));
		report.setLockedCount(rows.stream().mapToLong(row -> value(row.getLockedCount())).sum());
		report.setConfirmedCount(rows.stream().mapToLong(row -> value(row.getConfirmedCount())).sum());
		report.setReleasedCount(rows.stream().mapToLong(row -> value(row.getReleasedCount())).sum());
		report.setDiscountAmount(rows.stream().map(FirstOrderReportRowVO::getDiscountAmount)
			.filter(java.util.Objects::nonNull)
			.reduce(BigDecimal.ZERO, BigDecimal::add));
		report.setRows(rows);
		return report;
	}

	public List<FirstOrderReportRowVO> alerts(String tenantId, LocalDateTime begin, LocalDateTime end) {
		return report(tenantId, begin, end).getRows().stream()
			.filter(row -> Boolean.TRUE.equals(row.getAlert()))
			.toList();
	}

	private Map<String, PromotionActivity> loadActivities(String tenantId, List<Map<String, Object>> maps) {
		if (maps.isEmpty()) {
			return Collections.emptyMap();
		}
		List<String> ids = maps.stream().map(map -> text(map, "activity_id"))
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
		if (ids.isEmpty()) {
			return Collections.emptyMap();
		}
		List<PromotionActivity> activities = promotionActivityMapper.selectBatchIds(ids);
		Map<String, PromotionActivity> result = new HashMap<>();
		for (PromotionActivity activity : activities) {
			if (tenantId.equals(activity.getTenantId())) {
				result.put(activity.getId(), activity);
			}
		}
		return result;
	}

	private BigDecimal monthlyBudget(String rules) {
		if (!StringUtils.hasText(rules)) {
			return BigDecimal.ZERO;
		}
		try {
			JSONObject json = JSONUtil.parseObj(rules);
			BigDecimal budget = json.getBigDecimal("monthlyBudget");
			return budget == null ? BigDecimal.ZERO : budget;
		}
		catch (Exception ex) {
			log.warn("首单活动规则解析失败，月预算按 0 计", ex);
			return BigDecimal.ZERO;
		}
	}

	private static String text(Map<String, Object> map, String key) {
		Object value = map.get(key);
		if (value == null) {
			value = map.get(toCamel(key));
		}
		return value == null ? null : String.valueOf(value);
	}

	private static Long number(Map<String, Object> map, String key) {
		Object value = map.get(key);
		if (value == null) {
			value = map.get(toCamel(key));
		}
		return value instanceof Number number ? number.longValue() : 0L;
	}

	private static BigDecimal decimal(Map<String, Object> map, String key) {
		Object value = map.get(key);
		if (value == null) {
			value = map.get(toCamel(key));
		}
		return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value == null ? "0" : value.toString());
	}

	private static String toCamel(String key) {
		StringBuilder builder = new StringBuilder();
		boolean upper = false;
		for (char c : key.toCharArray()) {
			if (c == '_') {
				upper = true;
			}
			else {
				builder.append(upper ? Character.toUpperCase(c) : c);
				upper = false;
			}
		}
		return builder.toString();
	}

	private static long value(Long value) {
		return value == null ? 0L : value;
	}

}
