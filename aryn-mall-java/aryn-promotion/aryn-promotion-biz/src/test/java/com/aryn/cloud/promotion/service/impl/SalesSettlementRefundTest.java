package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.promotion.api.entity.SalesConfig;
import com.aryn.cloud.promotion.api.entity.SalesOrder;
import com.aryn.cloud.promotion.api.entity.SalesPayoutAdjustment;
import com.aryn.cloud.promotion.api.entity.SalesRefundRecord;
import com.aryn.cloud.promotion.service.IDistributionUserService;
import com.aryn.cloud.promotion.service.ISalesConfigService;
import com.aryn.cloud.promotion.service.ISalesCustomerBindService;
import com.aryn.cloud.promotion.service.ISalesCustomerClaimService;
import com.aryn.cloud.promotion.service.ISalesOrderService;
import com.aryn.cloud.promotion.service.ISalesPayoutAdjustmentService;
import com.aryn.cloud.promotion.service.ISalesPayoutService;
import com.aryn.cloud.promotion.service.ISalesRefundRecordService;
import com.aryn.cloud.promotion.service.ISalesStaffService;
import com.aryn.cloud.promotion.service.ISalesTransferRewardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 销售提成退款扣回单测：待确认/可发放直接冲减、已发放生成负数结转、全额退款、
 * 已入批业绩单重算待确认台账、首单全额退款扣回转交奖励、按 refundNo 幂等、退款早于支付留待重放。
 */
class SalesSettlementRefundTest {

	private ISalesOrderService salesOrderService;

	private ISalesRefundRecordService salesRefundRecordService;

	private ISalesPayoutAdjustmentService salesPayoutAdjustmentService;

	private ISalesPayoutService salesPayoutService;

	private ISalesTransferRewardService salesTransferRewardService;

	private SalesSettlementServiceImpl service;

	@BeforeEach
	void setUp() {
		ISalesStaffService salesStaffService = mock(ISalesStaffService.class);
		ISalesCustomerClaimService claimService = mock(ISalesCustomerClaimService.class);
		ISalesCustomerBindService bindService = mock(ISalesCustomerBindService.class);
		SalesBindWriter bindWriter = mock(SalesBindWriter.class);
		salesOrderService = mock(ISalesOrderService.class);
		ISalesConfigService configService = mock(ISalesConfigService.class);
		salesRefundRecordService = mock(ISalesRefundRecordService.class);
		salesTransferRewardService = mock(ISalesTransferRewardService.class);
		salesPayoutAdjustmentService = mock(ISalesPayoutAdjustmentService.class);
		salesPayoutService = mock(ISalesPayoutService.class);
		IDistributionUserService distributionUserService = mock(IDistributionUserService.class);
		service = new SalesSettlementServiceImpl(salesStaffService, claimService, bindService, bindWriter,
				salesOrderService, configService, salesRefundRecordService, salesTransferRewardService,
				salesPayoutAdjustmentService, salesPayoutService, distributionUserService);
		when(configService.getActiveConfigOrDefaults()).thenReturn(new SalesConfig()
			.setDefaultCommissionRate(new BigDecimal("0.0500"))
			.setRefundRiskDays(7));
		when(salesOrderService.updateById(any())).thenReturn(true);
		when(salesRefundRecordService.save(any())).thenReturn(true);
		when(salesRefundRecordService.updateById(any())).thenReturn(true);
	}

	@Test
	void pendingOrderRefundReducesCommissionDirectly() {
		SalesOrder order = salesOrder("0", new BigDecimal("100.00"), new BigDecimal("5.00"));
		when(salesOrderService.getByBizOrderId("order-1")).thenReturn(order);

		assertThat(service.handleRefund(refundEvent("refund-1", new BigDecimal("20.00")))).isTrue();

		assertThat(order.getRefundedBase()).isEqualByComparingTo("20.00");
		assertThat(order.getRefundedCommission()).isEqualByComparingTo("1.00");
		assertThat(order.getStatus()).isEqualTo("0");
		verify(salesPayoutAdjustmentService, never()).recordAdjustment(any(), any(), any(), any(), any());
		assertThat(capturedRecord().getApplied()).isEqualTo("1");
	}

	@Test
	void payableOrderRefundReducesCommissionDirectly() {
		SalesOrder order = salesOrder("1", new BigDecimal("100.00"), new BigDecimal("5.00"));
		when(salesOrderService.getByBizOrderId("order-1")).thenReturn(order);

		assertThat(service.handleRefund(refundEvent("refund-1", new BigDecimal("40.00")))).isTrue();

		assertThat(order.getRefundedBase()).isEqualByComparingTo("40.00");
		assertThat(order.getRefundedCommission()).isEqualByComparingTo("2.00");
		assertThat(order.getStatus()).isEqualTo("1");
		verify(salesPayoutService, never()).recalcPendingPayout(any());
		verify(salesTransferRewardService, never()).clawbackReward(any());
	}

	@Test
	void partialRefundDoesNotClawbackTransferReward() {
		SalesOrder order = salesOrder("1", new BigDecimal("100.00"), new BigDecimal("5.00"));
		when(salesOrderService.getByBizOrderId("order-1")).thenReturn(order);

		assertThat(service.handleRefund(refundEvent("refund-1", new BigDecimal("20.00")))).isTrue();

		verify(salesTransferRewardService, never()).clawbackReward(any());
	}

	@Test
	void fullRefundOfBatchedOrderRecalculatesPendingPayoutAndClawsBackReward() {
		SalesOrder order = salesOrder("1", new BigDecimal("100.00"), new BigDecimal("5.00"));
		order.setPayoutId("payout-1");
		when(salesOrderService.getByBizOrderId("order-1")).thenReturn(order);

		assertThat(service.handleRefund(refundEvent("refund-1", new BigDecimal("100.00")))).isTrue();

		assertThat(order.getStatus()).isEqualTo("3");
		assertThat(order.getRefundedCommission()).isEqualByComparingTo("5.00");
		// 已生成未确认台账需重算，避免财务按旧金额发放
		verify(salesPayoutService).recalcPendingPayout("payout-1");
		// 首单全额退款扣回原分销员转交奖励（幂等）
		verify(salesTransferRewardService).clawbackReward("order-1");
	}

	@Test
	void fullRefundWithoutPayoutStillClawsBackReward() {
		SalesOrder order = salesOrder("1", new BigDecimal("100.00"), new BigDecimal("5.00"));
		when(salesOrderService.getByBizOrderId("order-1")).thenReturn(order);

		assertThat(service.handleRefund(refundEvent("refund-1", new BigDecimal("100.00")))).isTrue();

		verify(salesPayoutService, never()).recalcPendingPayout(any());
		verify(salesTransferRewardService).clawbackReward("order-1");
	}

	@Test
	void paidOrderRefundCreatesNegativePayoutAdjustment() {
		SalesOrder order = salesOrder("2", new BigDecimal("100.00"), new BigDecimal("5.00"));
		order.setPayoutId("payout-1");
		when(salesOrderService.getByBizOrderId("order-1")).thenReturn(order);

		assertThat(service.handleRefund(refundEvent("refund-1", new BigDecimal("20.00")))).isTrue();

		ArgumentCaptor<BigDecimal> amount = ArgumentCaptor.forClass(BigDecimal.class);
		verify(salesPayoutAdjustmentService).recordAdjustment(eq("staff-1"), eq("李四"), eq("order-1"),
				eq("refund-1"), amount.capture());
		assertThat(amount.getValue()).isEqualByComparingTo("-1.00");
		assertThat(order.getRefundedCommission()).isEqualByComparingTo("1.00");
		assertThat(order.getStatus()).isEqualTo("2");
		// 已发放批次重算由发放台账域判断为 no-op
		verify(salesPayoutService).recalcPendingPayout("payout-1");
		verify(salesTransferRewardService, never()).clawbackReward(any());
	}

	@Test
	void fullRefundMarksOrderFullyRefunded() {
		SalesOrder order = salesOrder("1", new BigDecimal("100.00"), new BigDecimal("5.00"));
		when(salesOrderService.getByBizOrderId("order-1")).thenReturn(order);

		assertThat(service.handleRefund(refundEvent("refund-1", new BigDecimal("100.00")))).isTrue();

		assertThat(order.getStatus()).isEqualTo("3");
		assertThat(order.getRefundedCommission()).isEqualByComparingTo("5.00");
	}

	@Test
	void sameRefundNumberIsAppliedOnlyOnce() {
		SalesRefundRecord applied = new SalesRefundRecord().setRefundNo("refund-1")
			.setBizOrderId("order-1")
			.setApplied("1")
			.setRefundBaseAmount(new BigDecimal("20.00"));
		when(salesRefundRecordService.getByRefundNo("refund-1")).thenReturn(applied);

		assertThat(service.handleRefund(refundEvent("refund-1", new BigDecimal("20.00")))).isFalse();

		verify(salesOrderService, never()).updateById(any());
		verify(salesTransferRewardService, never()).clawbackReward(any());
	}

	@Test
	void refundBeforePaymentWaitsForReplay() {
		when(salesOrderService.getByBizOrderId("order-1")).thenReturn(null);

		assertThat(service.handleRefund(refundEvent("refund-1", new BigDecimal("20.00")))).isFalse();

		SalesRefundRecord record = capturedRecord();
		assertThat(record.getApplied()).isEqualTo("0");
		assertThat(record.getRefundBaseAmount()).isEqualByComparingTo("20.00");
	}

	@Test
	void pendingRefundRecordIsReplayedAfterSalesOrderCreated() {
		SalesRefundRecord record = new SalesRefundRecord().setId("record-1")
			.setRefundNo("refund-1")
			.setBizOrderId("order-1")
			.setRefundBaseAmount(new BigDecimal("20.00"))
			.setApplied("0")
			.setCreateTime(LocalDateTime.now().minusMinutes(1));
		when(salesRefundRecordService.getById("record-1")).thenReturn(record);
		SalesOrder order = salesOrder("0", new BigDecimal("100.00"), new BigDecimal("5.00"));
		when(salesOrderService.getByBizOrderId("order-1")).thenReturn(order);

		assertThat(service.replayPendingRefund("record-1")).isTrue();

		assertThat(order.getRefundedCommission()).isEqualByComparingTo("1.00");
		assertThat(record.getApplied()).isEqualTo("1");
	}

	private SalesOrder salesOrder(String status, BigDecimal base, BigDecimal commission) {
		return new SalesOrder().setId("sales-order-1")
			.setBizOrderId("order-1")
			.setBuyerUserId("buyer-1")
			.setSalesStaffId("staff-1")
			.setStaffName("李四")
			.setCommissionBase(base)
			.setCommissionAmount(commission)
			.setRefundedBase(BigDecimal.ZERO)
			.setRefundedCommission(BigDecimal.ZERO)
			.setStatus(status);
	}

	private OrderRefundSuccessEvent refundEvent(String refundNo, BigDecimal refundBaseAmount) {
		OrderRefundSuccessEvent event = new OrderRefundSuccessEvent();
		event.setOrderId("order-1");
		event.setRefundNo(refundNo);
		event.setRefundAmount(refundBaseAmount);
		event.setRefundBaseAmount(refundBaseAmount);
		event.setUserId("buyer-1");
		return event;
	}

	private SalesRefundRecord capturedRecord() {
		ArgumentCaptor<SalesRefundRecord> captor = ArgumentCaptor.forClass(SalesRefundRecord.class);
		verify(salesRefundRecordService).save(captor.capture());
		return captor.getValue();
	}

}
