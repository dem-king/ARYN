package com.aryn.cloud.order.listener;

import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import com.aryn.cloud.order.api.entity.OrderRefund;
import com.aryn.cloud.order.service.IDeliveryTaskService;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.order.service.IOrderItemService;
import com.aryn.cloud.order.service.IOrderRefundService;
import com.aryn.cloud.user.api.remote.RemotePointsService;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 退款积分返还口径（P0-4）：
 * 部分退款按明细积分分摊份额 floor 返还；全退补齐尾差，两路合计恰为 pointsUsed、不超返。
 */
@ExtendWith(MockitoExtension.class)
class ArynRefundListenerPointsRefundTest {

	@Mock
	private IOrderRefundService orderRefundService;

	@Mock
	private IOrderItemService orderItemService;

	@Mock
	private IOrderInfoService orderInfoService;

	@Mock
	private RocketMQTemplate rocketMQTemplate;

	@Mock
	private IDeliveryTaskService deliveryTaskService;

	@Mock
	private RemotePointsService remotePointsService;

	private ArynRefundListener listener;

	@BeforeEach
	void setUp() {
		listener = new ArynRefundListener(orderRefundService, orderItemService, orderInfoService, rocketMQTemplate,
				deliveryTaskService, remotePointsService);
	}

	/**
	 * 部分退款：2000 分抵 20 元，本明细分摊 5 元 → floor(2000×5/20)=500 分，
	 * bizNo 挂退款单号，同单 MQ 重投只返一次。
	 */
	@Test
	void partialRefundReturnsFloorShareByItemPointsPrice() {
		OrderInfo order = order(2000, "20.00");
		OrderItemEntity item = item("item-1", "5.00");
		OrderRefund refund = refund("refund-1", item);

		listener.refundPointsOnPartialRefund(order, refund, item);

		verify(remotePointsService).refundPoints(eq("user-1"), eq(500), eq("ORDER_REFUND:NO-1:refund-1"),
				anyString());
	}

	/**
	 * 未分摊积分的明细（黑名单/不可叠活动/0 元赠品）：pointsPrice 为 0，无可返还份额。
	 */
	@Test
	void partialRefundSkipsItemWithoutPointsShare() {
		OrderInfo order = order(2000, "20.00");
		OrderItemEntity item = item("item-1", "0.00");
		OrderRefund refund = refund("refund-1", item);

		listener.refundPointsOnPartialRefund(order, refund, item);

		verify(remotePointsService, never()).refundPoints(anyString(), anyInt(), anyString(), anyString());
	}

	/**
	 * 全退尾差补齐：另一明细已按部分退返还 floor 份额 500，本笔补 2000-500=1500，
	 * bizNo 沿用订单级幂等键（与取消路径共用，只退一次）。
	 */
	@Test
	void fullRefundPaysBackRoundingRemainder() {
		OrderInfo order = order(2000, "20.00");
		OrderItemEntity current = item("item-2", "15.00");
		OrderRefund refund = refund("refund-2", current);
		OrderItemEntity earlier = item("item-1", "5.00");
		when(orderItemService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
			.thenReturn(List.of(earlier, current));

		listener.refundRemainPointsOnFullRefund(order, refund, current);

		verify(remotePointsService).refundPoints(eq("user-1"), eq(1500), eq("ORDER_REFUND:NO-1"), anyString());
	}

	/**
	 * 单明细订单直退全退：无其他明细已返，补齐全额 pointsUsed。
	 */
	@Test
	void fullRefundSingleItemReturnsAllPoints() {
		OrderInfo order = order(2000, "20.00");
		OrderItemEntity current = item("item-1", "20.00");
		OrderRefund refund = refund("refund-1", current);
		when(orderItemService.list(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
			.thenReturn(List.of(current));

		listener.refundRemainPointsOnFullRefund(order, refund, current);

		verify(remotePointsService).refundPoints(eq("user-1"), eq(2000), eq("ORDER_REFUND:NO-1"), anyString());
	}

	/**
	 * 未使用积分（pointsUsed=0）或抵扣额缺失的订单：两条路径都不触发返还。
	 */
	@Test
	void noPointsUsedNeverRefunds() {
		OrderInfo order = new OrderInfo().setUserId("user-1").setOrderNo("NO-1").setPointsUsed(0)
			.setPointsDeductPrice(BigDecimal.ZERO);
		OrderItemEntity item = item("item-1", "0.00");
		OrderRefund refund = refund("refund-1", item);

		listener.refundPointsOnPartialRefund(order, refund, item);
		listener.refundRemainPointsOnFullRefund(order, refund, item);

		verify(remotePointsService, never()).refundPoints(anyString(), anyInt(), anyString(), anyString());
	}

	private OrderInfo order(int pointsUsed, String deductPrice) {
		return new OrderInfo().setUserId("user-1").setOrderNo("NO-1").setPointsUsed(pointsUsed)
			.setPointsDeductPrice(new BigDecimal(deductPrice));
	}

	private OrderItemEntity item(String id, String pointsPrice) {
		return new OrderItemEntity().setId(id).setPointsPrice(new BigDecimal(pointsPrice));
	}

	private OrderRefund refund(String id, OrderItemEntity item) {
		OrderRefund refund = new OrderRefund();
		refund.setId(id);
		refund.setOrderItemId(item.getId());
		return refund;
	}

}
