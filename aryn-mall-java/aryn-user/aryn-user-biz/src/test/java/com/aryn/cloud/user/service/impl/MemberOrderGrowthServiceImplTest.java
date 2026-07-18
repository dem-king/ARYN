package com.aryn.cloud.user.service.impl;

import com.aryn.cloud.common.core.entity.OrderCompleteEvent;
import com.aryn.cloud.user.api.entity.MemberOrderGrowth;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.mapper.MemberOrderGrowthMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IMemberLevelService;
import com.aryn.cloud.user.service.IPointsRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberOrderGrowthServiceImplTest {

	@Mock private MemberOrderGrowthMapper growthMapper;
	@Mock private UserInfoMapper userInfoMapper;
	@Mock private IPointsRecordService pointsRecordService;
	@Mock private IMemberLevelService memberLevelService;

	private MemberOrderGrowthServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new MemberOrderGrowthServiceImpl(growthMapper, userInfoMapper, pointsRecordService,
				memberLevelService);
	}

	@Test
	void duplicateOrderMessageDoesNothing() {
		when(growthMapper.insertIfAbsent(any(MemberOrderGrowth.class))).thenReturn(0);

		service.processOrderComplete(event());

		verify(userInfoMapper, never()).increaseTotalConsume(any(), any());
		verify(pointsRecordService, never()).recordPointsChange(any(), any(), any(), any(), any());
	}

	@Test
	void orderCompletionAwardsMultipliedPointsAndAccumulatesConsume() {
		when(growthMapper.insertIfAbsent(any(MemberOrderGrowth.class))).thenReturn(1);
		when(userInfoMapper.selectById("user-1")).thenReturn(new UserInfo().setId("user-1"));
		when(userInfoMapper.increaseTotalConsume("user-1", new BigDecimal("100.00"))).thenReturn(1);

		service.processOrderComplete(event());

		verify(pointsRecordService).recordPointsChange("user-1", "1", 200, "ORDER_COMPLETE", "订单完成积分奖励：NO-1");
		verify(userInfoMapper).increaseTotalConsume("user-1", new BigDecimal("100.00"));
	}

	private OrderCompleteEvent event() {
		OrderCompleteEvent event = new OrderCompleteEvent();
		event.setOrderId("order-1");
		event.setOrderNo("NO-1");
		event.setUserId("user-1");
		event.setTenantId("tenant-1");
		event.setGoodsPaymentAmount(new BigDecimal("100.00"));
		event.setPointsMultiplier(new BigDecimal("2.00"));
		return event;
	}

}
