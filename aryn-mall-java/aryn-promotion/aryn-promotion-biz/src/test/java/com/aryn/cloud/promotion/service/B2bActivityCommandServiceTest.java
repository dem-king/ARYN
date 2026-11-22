package com.aryn.cloud.promotion.service;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.B2bActivitySaveDTO;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.mapper.PromoCalendarMapper;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.service.impl.ActivityPublishGovernanceService;
import com.aryn.cloud.promotion.service.impl.B2bActivityCommandService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class B2bActivityCommandServiceTest {

	private final PromotionActivityMapper mapper = mock(PromotionActivityMapper.class);
	private final PromoCalendarMapper calendars = mock(PromoCalendarMapper.class);
	private final ActivityPublishGovernanceService governance = new ActivityPublishGovernanceService(mapper);
	private final B2bActivityCommandService commands = new B2bActivityCommandService(mapper, governance, calendars);

	@BeforeAll
	static void metadata() {
		TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), PromotionActivity.class);
	}

	@BeforeEach
	void tenant() { ArynTenantContextHolder.setTenantId("tenant-1"); }

	@AfterEach
	void cleanup() { ArynTenantContextHolder.removeTenantId(); }

	@ParameterizedTest
	@ValueSource(strings = {"1", "2", "3", "6", "8", "9", "10", "11", "99"})
	void rejectsRetiredAndSystemTypesBeforeWriting(String type) {
		B2bActivitySaveDTO dto = draft();
		dto.setActivityType(type);
		assertThatThrownBy(() -> commands.create(dto)).isInstanceOf(ArynBusinessException.class);
		verifyNoInteractions(mapper);
	}

	@Test
	void createsDraftWithServerOwnedState() {
		PromotionActivity saved = commands.create(draft());
		assertThat(saved.getTenantId()).isEqualTo("tenant-1");
		assertThat(saved.getStatus()).isEqualTo(PromotionActivity.STATUS_DRAFT);
		assertThat(saved.getRevision()).isZero();
		assertThat(saved.getConsumedBudget()).isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(saved.getConsumedQuota()).isZero();
		assertThat(saved.getCouponStackable()).isEqualTo("1");
		verify(mapper).insert(saved);
	}

	@Test
	void rejectsStaleUpdateAndPublish() {
		PromotionActivity saved = saved("7");
		saved.setRevision(3L);
		when(mapper.selectOne(any())).thenReturn(saved);
		B2bActivitySaveDTO dto = draft();
		dto.setExpectedRevision(2L);
		assertThatThrownBy(() -> commands.update("a-1", dto)).isInstanceOf(ArynBusinessException.class);
		assertThatThrownBy(() -> commands.publish("a-1", 2L, true)).isInstanceOf(ArynBusinessException.class);
		verify(mapper, never()).update(any(), any());
	}

	@Test
	void rejectsChangingExistingTypeToRetiredEvenWithValidRevision() {
		when(mapper.selectOne(any())).thenReturn(saved("7"));
		B2bActivitySaveDTO dto = draft();
		dto.setActivityType("9");
		dto.setExpectedRevision(0L);
		assertThatThrownBy(() -> commands.update("a-1", dto)).isInstanceOf(ArynBusinessException.class);
		verify(mapper, never()).update(any(), any());
	}

	@ParameterizedTest
	@ValueSource(strings = {"1", "2", "3", "6", "9", "10", "11"})
	void cannotCopyUpdateOrRepublishRetiredHistory(String type) {
		when(mapper.selectOne(any())).thenReturn(saved(type));
		B2bActivitySaveDTO dto = draft();
		dto.setExpectedRevision(0L);
		assertThatThrownBy(() -> commands.update("a-1", dto)).isInstanceOf(ArynBusinessException.class);
		assertThatThrownBy(() -> commands.copy("a-1", 0L)).isInstanceOf(ArynBusinessException.class);
		assertThatThrownBy(() -> commands.publish("a-1", 0L, true)).isInstanceOf(ArynBusinessException.class);
		verify(mapper, never()).insert(any(PromotionActivity.class));
		verify(mapper, never()).update(any(), any());
	}

	@Test
	void systemCodeCannotBeTakenOverEvenOnOtherwiseLegalType() {
		PromotionActivity saved = saved("7");
		saved.setActivityCode("SYS_CLEARANCE_ZONE");
		when(mapper.selectOne(any())).thenReturn(saved);
		assertThatThrownBy(() -> commands.publish("a-1", 0L, true)).isInstanceOf(ArynBusinessException.class);
		verify(mapper, never()).update(any(), any());
	}

	@Test
	void preservesConsumedAmountsAndPublishesWithNewRevision() {
		PromotionActivity saved = saved("7");
		saved.setConsumedBudget(new BigDecimal("12.50"));
		when(mapper.selectOne(any())).thenReturn(saved);
		when(mapper.update(any(), any())).thenReturn(1);
		commands.publish("a-1", 0L, true);
		assertThat(saved.getRevision()).isEqualTo(1L);
		assertThat(saved.getStatus()).isEqualTo(PromotionActivity.STATUS_PUBLISHED);
		assertThat(saved.getConsumedBudget()).isEqualByComparingTo("12.50");
	}

	@Test
	void casFailureDoesNotReportSuccess() {
		when(mapper.selectOne(any())).thenReturn(saved("7"));
		assertThatThrownBy(() -> commands.publish("a-1", 0L, true)).isInstanceOf(ArynBusinessException.class);
	}

	@Test
	void historicalRetiredActivityCanStillBeRead() {
		when(mapper.selectOne(any())).thenReturn(saved("9"));
		assertThat(commands.get("a-1").getActivityType()).isEqualTo("9");
	}

	@Test
	void budgetCannotBeReducedBelowConsumption() {
		PromotionActivity saved = saved("7");
		saved.setConsumedBudget(new BigDecimal("20"));
		when(mapper.selectOne(any())).thenReturn(saved);
		B2bActivitySaveDTO dto = draft();
		dto.setExpectedRevision(0L);
		dto.setTotalBudget(BigDecimal.TEN);
		assertThatThrownBy(() -> commands.update("a-1", dto)).isInstanceOf(ArynBusinessException.class);
	}

	private B2bActivitySaveDTO draft() {
		B2bActivitySaveDTO dto = new B2bActivitySaveDTO();
		dto.setActivityName("整船满额优惠");
		dto.setActivityType("7");
		dto.setScopeType("1");
		dto.setRules("{\"tiers\":[{\"minAmount\":100,\"discountAmount\":10}]}");
		dto.setStartTime(LocalDateTime.now().minusDays(1));
		dto.setEndTime(LocalDateTime.now().plusDays(1));
		return dto;
	}

	private PromotionActivity saved(String type) {
		PromotionActivity activity = new PromotionActivity();
		org.springframework.beans.BeanUtils.copyProperties(draft(), activity);
		activity.setId("a-1");
		activity.setTenantId("tenant-1");
		activity.setRevision(0L);
		activity.setActivityType(type);
		activity.setStatus(PromotionActivity.STATUS_DRAFT);
		return activity;
	}
}
