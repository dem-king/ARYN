package com.aryn.cloud.promotion.controller.admin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.service.impl.ActivityPublishGovernanceService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 船供营销活动管理端契约测试：展示态派生、筛选翻译与发布/暂停/删除守卫。
 */
@ExtendWith(MockitoExtension.class)
class ShipActivityControllerTest {

	@Mock
	private PromotionActivityMapper activityMapper;

	@Mock
	private ActivityPublishGovernanceService governanceService;

	private ShipActivityController controller;

	private MockedStatic<SecurityUtils> securityUtils;

	@BeforeAll
	static void initTableMeta() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
				PromotionActivity.class);
	}

	@BeforeEach
	void setUp() {
		controller = new ShipActivityController(activityMapper, governanceService);
		securityUtils = mockStatic(SecurityUtils.class);
		securityUtils.when(SecurityUtils::getTenantId).thenReturn("tenant-1");
	}

	@AfterEach
	void tearDown() {
		securityUtils.close();
	}

	private PromotionActivity activity(String status, LocalDateTime start, LocalDateTime end) {
		PromotionActivity activity = new PromotionActivity();
		activity.setId("act-1");
		activity.setTenantId("tenant-1");
		activity.setActivityName("测试活动");
		activity.setActivityType(PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT);
		activity.setStatus(status);
		activity.setStartTime(start);
		activity.setEndTime(end);
		return activity;
	}

	@Test
	@DisplayName("分页出参按时间窗派生展示态：过期→已结束、未开始→待开始，人工态原样保留")
	void pageDerivesDisplayStatusFromTimeWindow() {
		LocalDateTime now = LocalDateTime.now();
		PromotionActivity paused = activity(PromotionActivity.STATUS_PUBLISHED, now.minusDays(5), now.plusDays(5));
		paused.setStatus(PromotionActivity.STATUS_PAUSED);
		PromotionActivity draft = activity(PromotionActivity.STATUS_DRAFT, now.minusDays(1), now.minusSeconds(1));
		Page<PromotionActivity> result = new Page<>();
		result.setRecords(List.of(
				activity(PromotionActivity.STATUS_PUBLISHED, now.minusDays(1), now.plusDays(1)),
				activity(PromotionActivity.STATUS_PUBLISHED, now.minusDays(5), now.minusDays(1)),
				activity(PromotionActivity.STATUS_PUBLISHED, now.plusDays(1), now.plusDays(5)),
				paused, draft));
		when(activityMapper.selectPage(any(), any())).thenReturn(result);

		IPage<PromotionActivity> out = controller.page(new Page<>(), new PromotionActivity()).getData();

		assertThat(out.getRecords()).extracting(PromotionActivity::getStatus)
				.containsExactly(PromotionActivity.STATUS_PUBLISHED, PromotionActivity.STATUS_ENDED,
						PromotionActivity.STATUS_UPCOMING, PromotionActivity.STATUS_PAUSED,
						PromotionActivity.STATUS_DRAFT);
	}

	@Test
	@DisplayName("筛选「已结束」翻译为已发布且结束时间已过的条件，而非字面状态值")
	void pageTranslatesEndedFilterToTimeWindow() {
		when(activityMapper.selectPage(any(), any())).thenReturn(new Page<>());
		PromotionActivity query = new PromotionActivity();
		query.setStatus(PromotionActivity.STATUS_ENDED);

		controller.page(new Page<>(), query);

		AbstractWrapper<?, ?, ?> wrapper = capturedWrapper();
		assertThat(wrapper.getSqlSegment()).contains("status", "end_time");
		assertThat(wrapper.getParamNameValuePairs().values())
				.contains(PromotionActivity.STATUS_PUBLISHED)
				.doesNotContain(PromotionActivity.STATUS_ENDED);
	}

	@Test
	@DisplayName("筛选「待开始」翻译为已发布且开始时间未到的条件")
	void pageTranslatesUpcomingFilterToTimeWindow() {
		when(activityMapper.selectPage(any(), any())).thenReturn(new Page<>());
		PromotionActivity query = new PromotionActivity();
		query.setStatus(PromotionActivity.STATUS_UPCOMING);

		controller.page(new Page<>(), query);

		AbstractWrapper<?, ?, ?> wrapper = capturedWrapper();
		assertThat(wrapper.getSqlSegment()).contains("status", "start_time");
		assertThat(wrapper.getParamNameValuePairs().values())
				.contains(PromotionActivity.STATUS_PUBLISHED)
				.doesNotContain(PromotionActivity.STATUS_UPCOMING);
	}

	@Test
	@DisplayName("筛选人工态（如已暂停）保持字面等值查询，不附加时间窗")
	void pageKeepsLiteralFilterForStoredStatuses() {
		when(activityMapper.selectPage(any(), any())).thenReturn(new Page<>());
		PromotionActivity query = new PromotionActivity();
		query.setStatus(PromotionActivity.STATUS_PAUSED);

		controller.page(new Page<>(), query);

		AbstractWrapper<?, ?, ?> wrapper = capturedWrapper();
		assertThat(wrapper.getSqlSegment()).doesNotContain("end_time", "start_time");
		assertThat(wrapper.getParamNameValuePairs().values()).contains(PromotionActivity.STATUS_PAUSED);
	}

	@Test
	@DisplayName("详情出参同样派生展示态")
	void getByIdDerivesDisplayStatus() {
		when(activityMapper.selectOne(any())).thenReturn(
				activity(PromotionActivity.STATUS_PUBLISHED, LocalDateTime.now().minusDays(5),
						LocalDateTime.now().minusDays(1)));

		PromotionActivity out = controller.getById("act-1").getData();

		assertThat(out.getStatus()).isEqualTo(PromotionActivity.STATUS_ENDED);
	}

	@Test
	@DisplayName("发布拒绝时间窗已完全结束的活动")
	void publishRejectsExpiredTimeWindow() {
		when(activityMapper.selectOne(any())).thenReturn(
				activity(PromotionActivity.STATUS_DRAFT, LocalDateTime.now().minusDays(2),
						LocalDateTime.now().minusDays(1)));

		assertThatThrownBy(() -> controller.publish("act-1", false))
				.isInstanceOfSatisfying(ArynBusinessException.class,
						ex -> assertThat(ex.getMsg()).contains("结束时间已过"));
		verify(activityMapper, never()).updateById(any(PromotionActivity.class));
	}

	@Test
	@DisplayName("发布成功写入已发布状态与发布时间")
	void publishSetsStatusAndPublishTime() {
		when(activityMapper.selectOne(any())).thenReturn(
				activity(PromotionActivity.STATUS_DRAFT, LocalDateTime.now().minusDays(1),
						LocalDateTime.now().plusDays(1)));
		when(governanceService.listPublishConflicts(any(), any())).thenReturn(List.of());
		when(activityMapper.updateById(any(PromotionActivity.class))).thenReturn(1);

		controller.publish("act-1", false);

		ArgumentCaptor<PromotionActivity> captor = ArgumentCaptor.forClass(PromotionActivity.class);
		verify(activityMapper).updateById(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(PromotionActivity.STATUS_PUBLISHED);
		assertThat(captor.getValue().getPublishTime()).isNotNull();
	}

	@Test
	@DisplayName("删除放行已过期的已发布活动，仅拦截进行中的")
	void deleteAllowsExpiredButBlocksRunning() {
		PromotionActivity expired = activity(PromotionActivity.STATUS_PUBLISHED, LocalDateTime.now().minusDays(5),
				LocalDateTime.now().minusDays(1));
		when(activityMapper.selectOne(any())).thenReturn(expired);
		when(activityMapper.deleteById(any(Serializable.class))).thenReturn(1);

		controller.delete("act-1");
		verify(activityMapper).deleteById(any(Serializable.class));

		PromotionActivity running = activity(PromotionActivity.STATUS_PUBLISHED, LocalDateTime.now().minusDays(1),
				LocalDateTime.now().plusDays(1));
		when(activityMapper.selectOne(any())).thenReturn(running);

		assertThatThrownBy(() -> controller.delete("act-1"))
				.isInstanceOfSatisfying(ArynBusinessException.class,
						ex -> assertThat(ex.getMsg()).contains("请先暂停"));
		verify(activityMapper, times(1)).deleteById(any(Serializable.class));
	}

	@Test
	@DisplayName("暂停进行中的已发布活动：状态置为已暂停")
	void pauseStopsRunningActivity() {
		PromotionActivity running = activity(PromotionActivity.STATUS_PUBLISHED, LocalDateTime.now().minusDays(1),
				LocalDateTime.now().plusDays(1));
		when(activityMapper.selectOne(any())).thenReturn(running);
		when(activityMapper.updateById(any(PromotionActivity.class))).thenReturn(1);

		controller.pause("act-1");

		ArgumentCaptor<PromotionActivity> captor = ArgumentCaptor.forClass(PromotionActivity.class);
		verify(activityMapper).updateById(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(PromotionActivity.STATUS_PAUSED);
	}

	@Test
	@DisplayName("暂停已过期的已发布活动：直接拒绝")
	void pauseRejectsExpiredActivity() {
		PromotionActivity expired = activity(PromotionActivity.STATUS_PUBLISHED, LocalDateTime.now().minusDays(5),
				LocalDateTime.now().minusDays(1));
		when(activityMapper.selectOne(any())).thenReturn(expired);

		assertThatThrownBy(() -> controller.pause("act-1"))
				.isInstanceOfSatisfying(ArynBusinessException.class,
						ex -> assertThat(ex.getMsg()).contains("仅进行中"));
		verify(activityMapper, never()).updateById(any(PromotionActivity.class));
	}

	@SuppressWarnings("unchecked")
	private AbstractWrapper<?, ?, ?> capturedWrapper() {
		ArgumentCaptor<Wrapper<PromotionActivity>> captor = ArgumentCaptor.forClass(Wrapper.class);
		verify(activityMapper).selectPage(any(), captor.capture());
		return (AbstractWrapper<?, ?, ?>) captor.getValue();
	}

}
