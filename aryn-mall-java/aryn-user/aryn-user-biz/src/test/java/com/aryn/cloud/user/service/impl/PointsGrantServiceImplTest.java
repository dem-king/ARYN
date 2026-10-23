package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.aryn.cloud.user.api.entity.PointsConfig;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.enums.PointsSceneEnum;
import com.aryn.cloud.user.api.vo.PointsDeductContextVO;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IPointsConfigService;
import com.aryn.cloud.user.service.IPointsRecordService;
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

	private PointsGrantServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new PointsGrantServiceImpl(pointsConfigService, pointsRecordService, userInfoMapper);
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
