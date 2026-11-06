package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.aryn.cloud.common.core.entity.OrderPaySuccessEvent;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.api.entity.SalesConfig;
import com.aryn.cloud.promotion.api.entity.SalesCustomerBind;
import com.aryn.cloud.promotion.api.entity.SalesCustomerClaim;
import com.aryn.cloud.promotion.api.entity.SalesOrder;
import com.aryn.cloud.promotion.api.entity.SalesRefundRecord;
import com.aryn.cloud.promotion.api.entity.SalesStaff;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 销售提成计算与首单转交单测（纯 Mockito，不连 DB/MQ）
 */
class SalesSettlementServiceImplTest {

	private ISalesStaffService salesStaffService;

	private ISalesCustomerClaimService salesCustomerClaimService;

	private ISalesCustomerBindService salesCustomerBindService;

	private SalesBindWriter salesBindWriter;

	private ISalesOrderService salesOrderService;

	private ISalesConfigService salesConfigService;

	private ISalesRefundRecordService salesRefundRecordService;

	private ISalesTransferRewardService salesTransferRewardService;

	private ISalesPayoutAdjustmentService salesPayoutAdjustmentService;

	private ISalesPayoutService salesPayoutService;

	private IDistributionUserService distributionUserService;

	private SalesSettlementServiceImpl service;

	@BeforeEach
	void setUp() {
		salesStaffService = mock(ISalesStaffService.class);
		salesCustomerClaimService = mock(ISalesCustomerClaimService.class);
		salesCustomerBindService = mock(ISalesCustomerBindService.class);
		salesBindWriter = mock(SalesBindWriter.class);
		salesOrderService = mock(ISalesOrderService.class);
		salesConfigService = mock(ISalesConfigService.class);
		salesRefundRecordService = mock(ISalesRefundRecordService.class);
		salesTransferRewardService = mock(ISalesTransferRewardService.class);
		salesPayoutAdjustmentService = mock(ISalesPayoutAdjustmentService.class);
		salesPayoutService = mock(ISalesPayoutService.class);
		distributionUserService = mock(IDistributionUserService.class);
		service = new SalesSettlementServiceImpl(salesStaffService, salesCustomerClaimService,
				salesCustomerBindService, salesBindWriter, salesOrderService, salesConfigService,
				salesRefundRecordService, salesTransferRewardService, salesPayoutAdjustmentService,
				salesPayoutService, distributionUserService);
		when(salesConfigService.getActiveConfigOrDefaults()).thenReturn(new SalesConfig()
			.setDefaultCommissionRate(new BigDecimal("0.0500"))
			.setReferralRewardRate(new BigDecimal("0.0100"))
			.setRefundRiskDays(7)
			.setClaimExpireDays(90));
	}

	@Test
	void onlinePaymentUsesPaymentPriceMinusFreightAsCommissionBase() {
		givenActiveStaff(null);
		when(salesOrderService.saveIdempotent(any())).thenReturn(true);
		OrderPaySuccessEvent event = payEvent("1", new BigDecimal("110.00"), new BigDecimal("10.00"), null);

		assertThat(service.handlePaySuccess(event)).isTrue();

		SalesOrder order = capturedOrder();
		assertThat(order.getCommissionBase()).isEqualByComparingTo("100.00");
		assertThat(order.getCommissionRate()).isEqualByComparingTo("0.0500");
		assertThat(order.getCommissionAmount()).isEqualByComparingTo("5.00");
		assertThat(order.getPaymentAmount()).isEqualByComparingTo("110.00");
		assertThat(order.getFreightAmount()).isEqualByComparingTo("10.00");
		// 支付即「可发放」：settle_at 只作退款风险观察期标记，不再作为 N 天放行门槛
		assertThat(order.getStatus()).isEqualTo("1");
		assertThat(order.getSettleAt()).isEqualTo(event.getPaymentTime().plusDays(7));
		assertThat(order.getPayoutId()).isNull();
	}

	@Test
	void cashOnDeliveryUsesActualPayPriceMinusFreight() {
		givenActiveStaff(null);
		when(salesOrderService.saveIdempotent(any())).thenReturn(true);
		OrderPaySuccessEvent event = payEvent("3", new BigDecimal("200.00"), new BigDecimal("10.00"),
				new BigDecimal("110.00"));

		assertThat(service.handlePaySuccess(event)).isTrue();

		SalesOrder order = capturedOrder();
		assertThat(order.getCommissionBase()).isEqualByComparingTo("100.00");
		assertThat(order.getCommissionAmount()).isEqualByComparingTo("5.00");
		// 货到付款实付金额取 actualPayPrice（实收含运费），不取应收 paymentPrice
		assertThat(order.getPaymentAmount()).isEqualByComparingTo("110.00");
		assertThat(order.getFreightAmount()).isEqualByComparingTo("10.00");
	}

	@Test
	void cashOnDeliveryFallsBackToPaymentPriceWhenActualPayPriceMissing() {
		givenActiveStaff(null);
		when(salesOrderService.saveIdempotent(any())).thenReturn(true);
		OrderPaySuccessEvent event = payEvent("3", new BigDecimal("110.00"), new BigDecimal("10.00"), null);

		assertThat(service.handlePaySuccess(event)).isTrue();

		assertThat(capturedOrder().getCommissionBase()).isEqualByComparingTo("100.00");
	}

	@Test
	void missingStaffRateFallsBackToGlobalDefaultAndRoundsDown() {
		SalesStaff staff = activeStaff(null);
		when(salesStaffService.getById("staff-1")).thenReturn(staff);
		when(salesOrderService.saveIdempotent(any())).thenReturn(true);
		OrderPaySuccessEvent event = payEvent("1", new BigDecimal("99.99"), BigDecimal.ZERO, null);

		assertThat(service.handlePaySuccess(event)).isTrue();

		SalesOrder order = capturedOrder();
		assertThat(order.getCommissionRate()).isEqualByComparingTo("0.0500");
		// 99.99 * 0.05 = 4.9995，向下取整 4.99
		assertThat(order.getCommissionAmount()).isEqualByComparingTo("4.99");
	}

	@Test
	void staffPersonalRateOverridesGlobalDefault() {
		SalesStaff staff = activeStaff(new BigDecimal("0.1000"));
		when(salesStaffService.getById("staff-1")).thenReturn(staff);
		when(salesOrderService.saveIdempotent(any())).thenReturn(true);
		OrderPaySuccessEvent event = payEvent("1", new BigDecimal("90.00"), BigDecimal.ZERO, null);

		assertThat(service.handlePaySuccess(event)).isTrue();

		SalesOrder order = capturedOrder();
		assertThat(order.getCommissionRate()).isEqualByComparingTo("0.1000");
		assertThat(order.getCommissionAmount()).isEqualByComparingTo("9.00");
	}

	@Test
	void selfPurchaseIsExcluded() {
		SalesStaff staff = activeStaff(null);
		when(salesStaffService.getById("staff-1")).thenReturn(staff);
		OrderPaySuccessEvent event = payEvent("1", new BigDecimal("100.00"), BigDecimal.ZERO, null);
		event.setUserId("member-1");

		assertThat(service.handlePaySuccess(event)).isFalse();

		verify(salesOrderService, never()).saveIdempotent(any());
	}

	@Test
	void resignedStaffProducesNoNewPerformance() {
		SalesStaff staff = activeStaff(null);
		staff.setStatus("1");
		when(salesStaffService.getById("staff-1")).thenReturn(staff);
		OrderPaySuccessEvent event = payEvent("1", new BigDecimal("100.00"), BigDecimal.ZERO, null);

		assertThat(service.handlePaySuccess(event)).isFalse();

		verify(salesOrderService, never()).saveIdempotent(any());
	}

	@Test
	void zeroCommissionBaseIsSkipped() {
		givenActiveStaff(null);
		OrderPaySuccessEvent event = payEvent("1", new BigDecimal("10.00"), new BigDecimal("10.00"), null);

		assertThat(service.handlePaySuccess(event)).isFalse();

		verify(salesOrderService, never()).saveIdempotent(any());
	}

	@Test
	void duplicatePaymentEventIsIdempotent() {
		givenActiveStaff(null);
		when(salesOrderService.getByBizOrderId("order-1")).thenReturn(new SalesOrder().setId("sales-order-1"));
		OrderPaySuccessEvent event = payEvent("1", new BigDecimal("100.00"), BigDecimal.ZERO, null);

		assertThat(service.handlePaySuccess(event)).isFalse();

		verify(salesOrderService, never()).saveIdempotent(any());
	}

	@Test
	void delayedPaymentReopensAbandonedRefundRecordAndAppliesIt() {
		givenActiveStaff(null);
		when(salesOrderService.saveIdempotent(any())).thenReturn(true);
		// 退款记录曾因超过 7 天无业绩单被置为已应用（放弃）
		when(salesRefundRecordService.reopenAbandonedByBizOrderId("order-1")).thenReturn(1);
		SalesOrder persisted = new SalesOrder().setId("sales-order-1")
			.setBizOrderId("order-1")
			.setBuyerUserId("buyer-1")
			.setSalesStaffId("staff-1")
			.setStaffName("李四")
			.setCommissionBase(new BigDecimal("100.00"))
			.setCommissionAmount(new BigDecimal("5.00"))
			.setRefundedBase(BigDecimal.ZERO)
			.setRefundedCommission(BigDecimal.ZERO)
			.setStatus("1");
		// 首次幂等检查时业绩单还不存在；生成后可查到并补应用退款
		when(salesOrderService.getByBizOrderId("order-1")).thenReturn(null, persisted);
		when(salesOrderService.updateById(any())).thenReturn(true);
		SalesRefundRecord reopened = new SalesRefundRecord().setId("record-1")
			.setRefundNo("refund-1")
			.setBizOrderId("order-1")
			.setRefundBaseAmount(new BigDecimal("20.00"))
			.setApplied("0")
			.setCreateTime(LocalDateTime.now().minusDays(10));
		when(salesRefundRecordService.list(any(Wrapper.class))).thenReturn(List.of(reopened));
		when(salesRefundRecordService.updateById(any())).thenReturn(true);

		assertThat(service.handlePaySuccess(payEvent("1", new BigDecimal("100.00"), BigDecimal.ZERO, null))).isTrue();

		// 延迟支付到达后重开被放弃的退款记录并补扣提成，避免全额发放
		verify(salesRefundRecordService).reopenAbandonedByBizOrderId("order-1");
		assertThat(reopened.getApplied()).isEqualTo("1");
		assertThat(persisted.getRefundedBase()).isEqualByComparingTo("20.00");
		assertThat(persisted.getRefundedCommission()).isEqualByComparingTo("1.00");
	}

	@Test
	void firstPaidOrderCompletesClaimTransferAndGrantsReferralReward() {
		SalesStaff staff = activeStaff(new BigDecimal("0.0500"));
		when(salesStaffService.getById("staff-1")).thenReturn(staff);
		SalesCustomerClaim claim = new SalesCustomerClaim().setId("claim-1").setCustomerUserId("buyer-1")
			.setSalesStaffId("staff-1")
			.setStatus("0");
		when(salesCustomerClaimService.getPendingClaim("buyer-1", "staff-1")).thenReturn(claim);
		DistributionUser buyerDistribution = new DistributionUser().setUserId("buyer-1")
			.setInviterUserId("distributor-1");
		when(distributionUserService.getByUserId("buyer-1")).thenReturn(buyerDistribution);
		DistributionUser distributor = new DistributionUser().setUserId("distributor-1").setNickname("张三");
		when(distributionUserService.getByUserId("distributor-1")).thenReturn(distributor);
		when(salesTransferRewardService.grantReward(anyString(), anyString(), anyString(), anyString(), any(),
				any())).thenReturn(true);
		when(salesOrderService.saveIdempotent(any())).thenReturn(true);

		assertThat(service.handlePaySuccess(payEvent("1", new BigDecimal("110.00"), new BigDecimal("10.00"), null)))
			.isTrue();

		verify(salesBindWriter).upsertBind(eq("buyer-1"), eq(staff), eq("CLAIM"), isNull(),
				eq("分销员：张三"), eq("首单转交"));
		verify(salesCustomerClaimService).markTransferred("claim-1");
		verify(salesCustomerClaimService).invalidatePendingClaims("buyer-1");
		verify(salesTransferRewardService).grantReward(eq("buyer-1"), eq("distributor-1"), eq("staff-1"),
				eq("order-1"), eq(new BigDecimal("100.00")), eq(new BigDecimal("0.0100")));
	}

	@Test
	void claimReplacedByAnotherSellerStillTransfersByOrderSnapshot() {
		SalesStaff staff = activeStaff(new BigDecimal("0.0500"));
		when(salesStaffService.getById("staff-1")).thenReturn(staff);
		// 认领已被另一位销售替换：快照销售的认领已不存在，但仍按订单快照完成转交
		when(salesCustomerClaimService.getPendingClaim("buyer-1", "staff-1")).thenReturn(null);
		DistributionUser buyerDistribution = new DistributionUser().setUserId("buyer-1")
			.setInviterUserId("distributor-1");
		when(distributionUserService.getByUserId("buyer-1")).thenReturn(buyerDistribution);
		when(distributionUserService.getByUserId("distributor-1"))
			.thenReturn(new DistributionUser().setUserId("distributor-1").setNickname("张三"));
		when(salesTransferRewardService.grantReward(anyString(), anyString(), anyString(), anyString(), any(),
				any())).thenReturn(true);
		when(salesOrderService.saveIdempotent(any())).thenReturn(true);

		assertThat(service.handlePaySuccess(payEvent("1", new BigDecimal("100.00"), BigDecimal.ZERO, null))).isTrue();

		verify(salesBindWriter).upsertBind(eq("buyer-1"), eq(staff), eq("CLAIM"), isNull(),
				eq("分销员：张三"), eq("首单转交"));
		// 被替换的待认领统一作废
		verify(salesCustomerClaimService).invalidatePendingClaims("buyer-1");
		verify(salesCustomerClaimService, never()).markTransferred(anyString());
		verify(salesTransferRewardService).grantReward(eq("buyer-1"), eq("distributor-1"), eq("staff-1"),
				eq("order-1"), eq(new BigDecimal("100.00")), eq(new BigDecimal("0.0100")));
	}

	@Test
	void expiredClaimStillTransfersByOrderSnapshot() {
		SalesStaff staff = activeStaff(new BigDecimal("0.0500"));
		when(salesStaffService.getById("staff-1")).thenReturn(staff);
		// 认领已过期：getPendingClaim 返回 null，但订单快照仍是该销售，支付即完成转交
		when(salesCustomerClaimService.getPendingClaim("buyer-1", "staff-1")).thenReturn(null);
		when(distributionUserService.getByUserId("buyer-1"))
			.thenReturn(new DistributionUser().setUserId("buyer-1").setInviterUserId("distributor-1"));
		when(distributionUserService.getByUserId("distributor-1"))
			.thenReturn(new DistributionUser().setUserId("distributor-1").setNickname("张三"));
		when(salesTransferRewardService.grantReward(anyString(), anyString(), anyString(), anyString(), any(),
				any())).thenReturn(true);
		when(salesOrderService.saveIdempotent(any())).thenReturn(true);

		assertThat(service.handlePaySuccess(payEvent("1", new BigDecimal("100.00"), BigDecimal.ZERO, null))).isTrue();

		verify(salesBindWriter).upsertBind(eq("buyer-1"), eq(staff), eq("CLAIM"), isNull(),
				eq("分销员：张三"), eq("首单转交"));
		verify(salesCustomerClaimService).invalidatePendingClaims("buyer-1");
		verify(salesTransferRewardService).grantReward(anyString(), anyString(), anyString(), anyString(), any(),
				any());
	}

	@Test
	void customerAlreadyBoundToOtherStaffKeepsBackendAttribution() {
		SalesStaff staff = activeStaff(new BigDecimal("0.0500"));
		when(salesStaffService.getById("staff-1")).thenReturn(staff);
		when(salesCustomerBindService.getByCustomerUserId("buyer-1"))
			.thenReturn(new SalesCustomerBind().setId("bind-1").setCustomerUserId("buyer-1")
				.setSalesStaffId("staff-2")
				.setBindSource("ADMIN"));
		when(salesOrderService.saveIdempotent(any())).thenReturn(true);

		assertThat(service.handlePaySuccess(payEvent("1", new BigDecimal("100.00"), BigDecimal.ZERO, null))).isTrue();

		// 不覆盖后台归属，也不发转交奖励
		verify(salesBindWriter, never()).upsertBind(anyString(), any(), anyString(), any(), any(), anyString());
		verify(salesTransferRewardService, never()).grantReward(anyString(), anyString(), anyString(), anyString(),
				any(), any());
	}

	@Test
	void customerAlreadyBoundToSameStaffDoesNotTransferAgain() {
		SalesStaff staff = activeStaff(new BigDecimal("0.0500"));
		when(salesStaffService.getById("staff-1")).thenReturn(staff);
		when(salesCustomerBindService.getByCustomerUserId("buyer-1"))
			.thenReturn(new SalesCustomerBind().setId("bind-1").setCustomerUserId("buyer-1")
				.setSalesStaffId("staff-1")
				.setBindSource("SHARE"));
		when(salesOrderService.saveIdempotent(any())).thenReturn(true);

		assertThat(service.handlePaySuccess(payEvent("1", new BigDecimal("100.00"), BigDecimal.ZERO, null))).isTrue();

		verify(salesBindWriter, never()).upsertBind(anyString(), any(), anyString(), any(), any(), anyString());
		verify(salesCustomerClaimService, never()).markTransferred(anyString());
		verify(salesCustomerClaimService, never()).invalidatePendingClaims(anyString());
		verify(salesTransferRewardService, never()).grantReward(anyString(), anyString(), anyString(), anyString(),
				any(), any());
	}

	@Test
	void abnormalCustomerWithoutBindOrDistributorStillCompletesAttribution() {
		SalesStaff staff = activeStaff(new BigDecimal("0.0500"));
		when(salesStaffService.getById("staff-1")).thenReturn(staff);
		when(distributionUserService.getByUserId("buyer-1")).thenReturn(null);
		when(salesOrderService.saveIdempotent(any())).thenReturn(true);

		assertThat(service.handlePaySuccess(payEvent("1", new BigDecimal("100.00"), BigDecimal.ZERO, null))).isTrue();

		verify(salesBindWriter).upsertBind(eq("buyer-1"), eq(staff), eq("CLAIM"), isNull(), isNull(),
				eq("首单转交"));
		// 无原分销员不发奖励
		verify(salesTransferRewardService, never()).grantReward(anyString(), anyString(), anyString(), anyString(),
				any(), any());
	}

	private void givenActiveStaff(BigDecimal commissionRate) {
		SalesStaff staff = activeStaff(commissionRate);
		when(salesStaffService.getById("staff-1")).thenReturn(staff);
		when(salesOrderService.getByBizOrderId("order-1")).thenReturn(null);
	}

	private SalesStaff activeStaff(BigDecimal commissionRate) {
		return new SalesStaff().setId("staff-1")
			.setStaffName("李四")
			.setDeptId("dept-1")
			.setMemberUserId("member-1")
			.setCommissionRate(commissionRate)
			.setStatus("0");
	}

	private OrderPaySuccessEvent payEvent(String paymentType, BigDecimal paymentPrice, BigDecimal freightPrice,
			BigDecimal actualPayPrice) {
		OrderPaySuccessEvent event = new OrderPaySuccessEvent();
		event.setOrderId("order-1");
		event.setOrderNo("NO-1");
		event.setUserId("buyer-1");
		event.setPaymentType(paymentType);
		event.setPaymentPrice(paymentPrice);
		event.setFreightPrice(freightPrice);
		event.setActualPayPrice(actualPayPrice);
		event.setSalesStaffId("staff-1");
		event.setPaymentTime(LocalDateTime.of(2026, 11, 5, 10, 30));
		return event;
	}

	private SalesOrder capturedOrder() {
		ArgumentCaptor<SalesOrder> captor = ArgumentCaptor.forClass(SalesOrder.class);
		verify(salesOrderService).saveIdempotent(captor.capture());
		return captor.getValue();
	}

}
