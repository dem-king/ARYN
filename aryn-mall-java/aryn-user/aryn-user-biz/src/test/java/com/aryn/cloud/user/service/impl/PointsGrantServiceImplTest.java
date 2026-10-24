package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.aryn.cloud.user.api.entity.PointsConfig;
import com.aryn.cloud.user.api.entity.PointsExclude;
import com.aryn.cloud.user.api.entity.PointsGlobalConfig;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.enums.PointsSceneEnum;
import com.aryn.cloud.user.api.vo.MemberBenefitsVO;
import com.aryn.cloud.user.api.vo.PointsDeductContextVO;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IMemberBenefitService;
import com.aryn.cloud.user.service.IPointsConfigService;
import com.aryn.cloud.user.service.IPointsExcludeService;
import com.aryn.cloud.user.service.IPointsGlobalConfigService;
import com.aryn.cloud.user.service.IPointsRecordService;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PointsGrantServiceImplTest {

	@Mock private IPointsConfigService pointsConfigService;
	@Mock private IPointsRecordService pointsRecordService;
	@Mock private UserInfoMapper userInfoMapper;
	@Mock private IPointsGlobalConfigService pointsGlobalConfigService;
	@Mock private IPointsExcludeService pointsExcludeService;
	@Mock private IMemberBenefitService memberBenefitService;

	private PointsGrantServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new PointsGrantServiceImpl(pointsConfigService, pointsRecordService, userInfoMapper,
				pointsGlobalConfigService, pointsExcludeService, memberBenefitService);
	}

	/**
	 * 等级专属抵扣上限覆盖全局默认（京东分档口径）：权益取到合法比例时覆盖，
	 * 非法区间不覆盖；等级服务异常按全局配置降级。
	 */
	@Test
	void deductContextOverridesRatioByMemberLevel() {
		stubRules(List.of(rule("ORDER_DEDUCT", "2", 100, "0")));
		when(userInfoMapper.selectById("user-1")).thenReturn(new UserInfo().setId("user-1").setPoint(5000));
		when(pointsGlobalConfigService.getEffectiveConfig())
			.thenReturn(new PointsGlobalConfig().setDeductMaxRatio(new BigDecimal("20.00")));
		MemberBenefitsVO benefits = new MemberBenefitsVO();
		benefits.setDeductMaxRatio(new BigDecimal("30.00"));
		when(memberBenefitService.getUserBenefits("user-1")).thenReturn(benefits);

		PointsDeductContextVO context = service.getDeductContext("user-1");

		assertThat(context.getDeductMaxRatio()).isEqualByComparingTo("30.00");
	}

	@Test
	void deductContextIgnoresInvalidLevelRatio() {
		stubRules(List.of(rule("ORDER_DEDUCT", "2", 100, "0")));
		when(userInfoMapper.selectById("user-1")).thenReturn(new UserInfo().setId("user-1").setPoint(5000));
		when(pointsGlobalConfigService.getEffectiveConfig())
			.thenReturn(new PointsGlobalConfig().setDeductMaxRatio(new BigDecimal("20.00")));
		MemberBenefitsVO benefits = new MemberBenefitsVO();
		benefits.setDeductMaxRatio(new BigDecimal("120"));
		when(memberBenefitService.getUserBenefits("user-1")).thenReturn(benefits);

		PointsDeductContextVO context = service.getDeductContext("user-1");

		assertThat(context.getDeductMaxRatio()).isEqualByComparingTo("20.00");
	}

	@Test
	void deductContextFallsBackWhenBenefitServiceFails() {
		stubRules(List.of(rule("ORDER_DEDUCT", "2", 100, "0")));
		when(userInfoMapper.selectById("user-1")).thenReturn(new UserInfo().setId("user-1").setPoint(5000));
		when(pointsGlobalConfigService.getEffectiveConfig())
			.thenReturn(new PointsGlobalConfig().setDeductMaxRatio(new BigDecimal("20.00")));
		when(memberBenefitService.getUserBenefits("user-1")).thenThrow(new IllegalStateException("权益库抖动"));

		PointsDeductContextVO context = service.getDeductContext("user-1");

		assertThat(context.isEnabled()).isTrue();
		assertThat(context.getDeductMaxRatio()).isEqualByComparingTo("20.00");
	}

	/**
	 * 抵扣上下文携带风控四参数与品类黑名单：订单域一次 RPC 拿全，
	 * 风控配置读取失败时按缺省继续（不阻断下单）。
	 */
	@Test
	void deductContextCarriesRiskControlConfigAndExclusions() {
		stubRules(List.of(rule("ORDER_DEDUCT", "2", 100, "0")));
		when(userInfoMapper.selectById("user-1")).thenReturn(new UserInfo().setId("user-1").setPoint(5000));
		PointsGlobalConfig global = new PointsGlobalConfig().setValidityDays(365)
				.setDeductMaxRatio(new BigDecimal("10.00"))
				.setMinUseAmount(new BigDecimal("10"))
				.setMaxDeductAmount(new BigDecimal("5.00"));
		when(pointsGlobalConfigService.getEffectiveConfig()).thenReturn(global);
		when(pointsExcludeService.listAll()).thenReturn(List.of(
				new PointsExclude().setTargetType(PointsExclude.TYPE_CATEGORY).setTargetId("cat-1"),
				new PointsExclude().setTargetType(PointsExclude.TYPE_GOODS).setTargetId("spu-1")));

		PointsDeductContextVO context = service.getDeductContext("user-1");

		assertThat(context.isEnabled()).isTrue();
		assertThat(context.getAvailablePoints()).isEqualTo(5000);
		assertThat(context.getPointsPerYuan()).isEqualTo(100);
		assertThat(context.getDeductMaxRatio()).isEqualByComparingTo("10.00");
		assertThat(context.getMinUseAmount()).isEqualByComparingTo("10");
		assertThat(context.getMaxDeductAmount()).isEqualByComparingTo("5.00");
		assertThat(context.getExcludeCategoryIds()).containsExactly("cat-1");
		assertThat(context.getExcludeSpuIds()).containsExactly("spu-1");
	}

	@Test
	void deductContextKeepsWorkingWhenRiskControlConfigFails() {
		stubRules(List.of(rule("ORDER_DEDUCT", "2", 100, "0")));
		when(userInfoMapper.selectById("user-1")).thenReturn(new UserInfo().setId("user-1").setPoint(5000));
		when(pointsGlobalConfigService.getEffectiveConfig()).thenThrow(new IllegalStateException("配置库不可用"));

		PointsDeductContextVO context = service.getDeductContext("user-1");

		assertThat(context.isEnabled()).isTrue();
		assertThat(context.getDeductMaxRatio()).isNull();
		assertThat(context.getExcludeCategoryIds()).isNull();
	}

	@Test
	void orderCompleteFallsBackToLegacyWhenNoRuleExists() {
		stubRules(Collections.emptyList());

		int granted = service.grant("user-1", PointsSceneEnum.ORDER_COMPLETE, "ORDER_COMPLETE:NO-1", 120,
				"订单完成积分奖励");

		assertThat(granted).isEqualTo(120);
		verify(pointsRecordService).recordPointsChange("user-1", "1", 120, "ORDER_COMPLETE", "订单完成积分奖励",
				"ORDER_COMPLETE:NO-1");
	}

	@Test
	void disabledRuleStopsGranting() {
		stubRules(List.of(rule("ORDER_COMPLETE", "1", 1, "1")));

		int granted = service.grant("user-1", PointsSceneEnum.ORDER_COMPLETE, "ORDER_COMPLETE:NO-1", 120,
				"订单完成积分奖励");

		assertThat(granted).isZero();
		verify(pointsRecordService, never()).recordPointsChange(anyString(), anyString(), anyInt(), anyString(),
				anyString(), anyString());
	}

	@Test
	void fixedSceneUsesConfiguredValue() {
		stubRules(List.of(rule("REGISTER", "1", 100, "0")));

		int granted = service.grant("user-1", PointsSceneEnum.REGISTER, "REGISTER:user-1", null, "注册奖励");

		assertThat(granted).isEqualTo(100);
		verify(pointsRecordService).recordPointsChange("user-1", "1", 100, "REGISTER", "注册奖励",
				"REGISTER:user-1");
	}

	@Test
	void orderCompleteRateFallsBackToOneAndHonorsDisabledRule() {
		stubRules(Collections.emptyList());
		assertThat(service.getOrderCompleteRate()).isEqualTo(1);

		stubRules(List.of(rule("ORDER_COMPLETE", "1", 2, "1")));
		assertThat(service.getOrderCompleteRate()).isZero();

		stubRules(List.of(rule("ORDER_COMPLETE", "1", 3, "0")));
		assertThat(service.getOrderCompleteRate()).isEqualTo(3);
	}

	@Test
	void deductContextReportsAvailablePointsAndRate() {
		stubRules(List.of(rule("ORDER_DEDUCT", "2", 100, "0")));
		when(userInfoMapper.selectById("user-1")).thenReturn(new UserInfo().setPoint(860));

		PointsDeductContextVO context = service.getDeductContext("user-1");

		assertThat(context.isEnabled()).isTrue();
		assertThat(context.getAvailablePoints()).isEqualTo(860);
		assertThat(context.getPointsPerYuan()).isEqualTo(100);
	}

	@Test
	void deductWritesSpendRecordWithBizNo() {
		service.deduct("user-1", 500, "ORDER_DEDUCT:NO-1", "订单积分抵现");

		verify(pointsRecordService).recordPointsChange(eq("user-1"), eq("2"), eq(500), eq("ORDER_DEDUCT"),
				eq("订单积分抵现"), eq("ORDER_DEDUCT:NO-1"));
	}

	@Test
	void refundWritesEarnRecordWithBizNo() {
		service.refund("user-1", 500, "ORDER_REFUND:NO-1", "订单取消返还积分");

		verify(pointsRecordService).recordPointsChange(eq("user-1"), eq("1"), eq(500), eq("ORDER_REFUND"),
				eq("订单取消返还积分"), eq("ORDER_REFUND:NO-1"));
	}

	@SuppressWarnings("unchecked")
	private void stubRules(List<PointsConfig> rules) {
		when(pointsConfigService.list(any(Wrapper.class))).thenReturn(rules);
	}

	private PointsConfig rule(String scene, String ruleType, int pointValue, String status) {
		return new PointsConfig()
			.setTriggerScene(scene)
			.setRuleType(ruleType)
			.setPointValue(pointValue)
			.setStatus(status);
	}

}
