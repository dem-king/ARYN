package com.aryn.cloud.promotion.service;

import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.vo.FirstOrderReportRowVO;
import com.aryn.cloud.promotion.api.vo.FirstOrderReportVO;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.mapper.PromotionLockMapper;
import com.aryn.cloud.promotion.service.impl.PromotionLockUsage;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 首单核销报表统计口径测试。
 *
 * <p>报表是只读聚合，不需要数据库；这里钉死「活动 × 主体类型」的计数、
 * 已核销让利只算 CONFIRMED、月预算使用率 ≥80% 预警三条口径。
 */
@DisplayName("首单核销报表")
class FirstOrderReportServiceTest {

	private PromotionLockMapper promotionLockMapper;

	private PromotionActivityMapper promotionActivityMapper;

	private PromotionLockUsage promotionLockUsage;

	private FirstOrderReportService service;

	@BeforeEach
	void setUp() {
		promotionLockMapper = mock(PromotionLockMapper.class);
		promotionActivityMapper = mock(PromotionActivityMapper.class);
		promotionLockUsage = mock(PromotionLockUsage.class);
		service = new FirstOrderReportService(promotionLockMapper, promotionActivityMapper, promotionLockUsage);
	}

	@Test
	@DisplayName("按活动×主体聚合并计算月预算使用率与预警")
	void aggregatesByActivityAndSubject() {
		Map<String, Object> row = new HashMap<>();
		row.put("activity_id", "act-ship");
		row.put("activity_name", "船供首单立减");
		row.put("subject_type", "1");
		row.put("total_count", 3L);
		row.put("locked_count", 1L);
		row.put("confirmed_count", 2L);
		row.put("released_count", 0L);
		row.put("subject_count", 2L);
		row.put("discount_amount", new BigDecimal("900.00"));
		when(promotionLockMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of(row));

		PromotionActivity activity = new PromotionActivity();
		activity.setId("act-ship");
		activity.setTenantId("tenant-1");
		activity.setActivityName("船供首单立减");
		activity.setRules("{\"monthlyBudget\":50000,\"firstOrderOnly\":true,\"subjectType\":\"1\"}");
		when(promotionActivityMapper.selectBatchIds(any())).thenReturn(List.of(activity));
		when(promotionLockUsage.sumDiscountSince(eq("tenant-1"), eq("act-ship"), any(LocalDateTime.class)))
			.thenReturn(new BigDecimal("42000.00"));

		LocalDateTime end = LocalDateTime.now();
		FirstOrderReportVO report = service.report("tenant-1", end.minusDays(7), end);

		assertThat(report.getLockedCount()).isEqualTo(1);
		assertThat(report.getConfirmedCount()).isEqualTo(2);
		assertThat(report.getReleasedCount()).isZero();
		assertThat(report.getDiscountAmount()).isEqualByComparingTo("900.00");
		FirstOrderReportRowVO detail = report.getRows().get(0);
		assertThat(detail.getActivityName()).isEqualTo("船供首单立减");
		assertThat(detail.getSubjectType()).isEqualTo("1");
		assertThat(detail.getMonthlyBudget()).isEqualByComparingTo("50000");
		assertThat(detail.getMonthlyUsed()).isEqualByComparingTo("42000");
		assertThat(detail.getUsageRate()).isEqualByComparingTo("84.00");
		assertThat(detail.getAlert()).isTrue();
	}

	@Test
	@DisplayName("未配置月预算时使用率为空且不预警")
	void noBudgetMeansNoAlert() {
		Map<String, Object> row = new HashMap<>();
		row.put("activity_id", "act-org");
		row.put("subject_type", "2");
		row.put("locked_count", 0L);
		row.put("confirmed_count", 1L);
		row.put("released_count", 0L);
		row.put("subject_count", 1L);
		row.put("discount_amount", new BigDecimal("100.00"));
		when(promotionLockMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of(row));

		PromotionActivity activity = new PromotionActivity();
		activity.setId("act-org");
		activity.setTenantId("tenant-1");
		activity.setRules("{\"tiers\":[]}");
		when(promotionActivityMapper.selectBatchIds(any())).thenReturn(List.of(activity));

		FirstOrderReportVO report = service.report("tenant-1", null, null);

		FirstOrderReportRowVO detail = report.getRows().get(0);
		assertThat(detail.getMonthlyBudget()).isEqualByComparingTo("0");
		assertThat(detail.getUsageRate()).isNull();
		assertThat(detail.getAlert()).isFalse();
	}

	@Test
	@DisplayName("预算预警只返回达到 80% 的活动行")
	void alertsFiltersRowsBelowThreshold() {
		Map<String, Object> high = new HashMap<>();
		high.put("activity_id", "act-high");
		high.put("subject_type", "1");
		high.put("discount_amount", BigDecimal.ZERO);
		Map<String, Object> low = new HashMap<>();
		low.put("activity_id", "act-low");
		low.put("subject_type", "2");
		low.put("discount_amount", BigDecimal.ZERO);
		when(promotionLockMapper.selectMaps(any(Wrapper.class))).thenReturn(List.of(high, low));

		PromotionActivity highActivity = new PromotionActivity();
		highActivity.setId("act-high");
		highActivity.setTenantId("tenant-1");
		highActivity.setRules("{\"monthlyBudget\":1000}");
		PromotionActivity lowActivity = new PromotionActivity();
		lowActivity.setId("act-low");
		lowActivity.setTenantId("tenant-1");
		lowActivity.setRules("{\"monthlyBudget\":1000}");
		when(promotionActivityMapper.selectBatchIds(any())).thenReturn(List.of(highActivity, lowActivity));
		when(promotionLockUsage.sumDiscountSince(anyString(), eq("act-high"), any(LocalDateTime.class)))
			.thenReturn(new BigDecimal("900"));
		when(promotionLockUsage.sumDiscountSince(anyString(), eq("act-low"), any(LocalDateTime.class)))
			.thenReturn(new BigDecimal("100"));

		List<FirstOrderReportRowVO> alerts = service.alerts("tenant-1", null, null);
		assertThat(alerts).hasSize(1);
		assertThat(alerts.get(0).getActivityId()).isEqualTo("act-high");
	}

}
