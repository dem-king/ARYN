package com.aryn.cloud.user.service.impl;

import com.aryn.cloud.common.core.entity.OrderCompleteEvent;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.MemberOrderGrowth;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.enums.PointsSceneEnum;
import com.aryn.cloud.user.mapper.MemberOrderGrowthMapper;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import com.aryn.cloud.user.service.IMemberLevelService;
import com.aryn.cloud.user.service.IPointsGrantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberOrderGrowthServiceImplTest {

	@Mock private MemberOrderGrowthMapper growthMapper;
	@Mock private UserInfoMapper userInfoMapper;
	@Mock private IPointsGrantService pointsGrantService;
	@Mock private IMemberLevelService memberLevelService;

	private MemberOrderGrowthServiceImpl service;

	@BeforeEach
	void setUp() {
		service = new MemberOrderGrowthServiceImpl(growthMapper, userInfoMapper, pointsGrantService,
				memberLevelService);
	}

	@Test
	void duplicateOrderMessageDoesNothing() {
		when(growthMapper.insertIfAbsent(any(MemberOrderGrowth.class))).thenReturn(0);

		service.processOrderComplete(event());

		verify(userInfoMapper, never()).increaseTotalConsume(any(), any());
		verify(pointsGrantService, never()).grant(anyString(), any(), anyString(), any(), anyString());
	}

	@Test
	void orderCompletionAwardsMultipliedPointsAndAccumulatesConsume() {
		when(growthMapper.insertIfAbsent(any(MemberOrderGrowth.class))).thenReturn(1);
		when(userInfoMapper.selectById("user-1")).thenReturn(new UserInfo().setId("user-1"));
		when(userInfoMapper.increaseTotalConsume("user-1", new BigDecimal("100.00"))).thenReturn(1);
		when(pointsGrantService.getOrderCompleteRate()).thenReturn(1);

		service.processOrderComplete(event());

		verify(pointsGrantService).grant("user-1", PointsSceneEnum.ORDER_COMPLETE, "ORDER_COMPLETE:NO-1", 200,
				"订单完成积分奖励：NO-1");
		verify(userInfoMapper).increaseTotalConsume("user-1", new BigDecimal("100.00"));
	}

	/**
	 * 返积分基数剔除黑名单：事件携带 grantBaseAmount 时积分按剔除后基数发放，
	 * 消费累计仍走全口径 goodsPaymentAmount（成长值不受黑名单影响）。
	 */
	@Test
	void grantUsesBlacklistExcludedBaseWhenPresent() {
		when(growthMapper.insertIfAbsent(any(MemberOrderGrowth.class))).thenReturn(1);
		when(userInfoMapper.selectById("user-1")).thenReturn(new UserInfo().setId("user-1"));
		when(userInfoMapper.increaseTotalConsume("user-1", new BigDecimal("100.00"))).thenReturn(1);
		when(pointsGrantService.getOrderCompleteRate()).thenReturn(1);
		OrderCompleteEvent orderCompleteEvent = event();
		orderCompleteEvent.setGrantBaseAmount(new BigDecimal("60.00"));

		service.processOrderComplete(orderCompleteEvent);

		verify(pointsGrantService).grant("user-1", PointsSceneEnum.ORDER_COMPLETE, "ORDER_COMPLETE:NO-1", 120,
				"订单完成积分奖励：NO-1");
		verify(userInfoMapper).increaseTotalConsume("user-1", new BigDecimal("100.00"));
	}

	/**
	 * 事件未携带基数（存量消息/灰度期）回退全口径，行为与旧消息完全一致。
	 */
	@Test
	void grantFallsBackToFullAmountWhenBaseMissing() {
		when(growthMapper.insertIfAbsent(any(MemberOrderGrowth.class))).thenReturn(1);
		when(userInfoMapper.selectById("user-1")).thenReturn(new UserInfo().setId("user-1"));
		when(userInfoMapper.increaseTotalConsume("user-1", new BigDecimal("100.00"))).thenReturn(1);
		when(pointsGrantService.getOrderCompleteRate()).thenReturn(1);

		service.processOrderComplete(event());

		verify(pointsGrantService).grant("user-1", PointsSceneEnum.ORDER_COMPLETE, "ORDER_COMPLETE:NO-1", 200,
				"订单完成积分奖励：NO-1");
	}

	@Test
	void rejectsGrantBaseAboveGoodsPaymentAmount() {
		OrderCompleteEvent orderCompleteEvent = event();
		orderCompleteEvent.setGrantBaseAmount(new BigDecimal("150.00"));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.processOrderComplete(orderCompleteEvent));
		assertEquals("订单返积分基数不合法", exception.getMsg());
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
