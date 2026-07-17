package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.aryn.cloud.promotion.api.entity.DistributionCommissionFlow;
import com.aryn.cloud.promotion.api.entity.DistributionOrder;
import com.aryn.cloud.promotion.api.entity.DistributionRefundRecord;
import com.aryn.cloud.promotion.api.entity.DistributionUser;
import com.aryn.cloud.promotion.api.enums.DistributionOrderStatusEnum;
import com.aryn.cloud.promotion.service.IDistributionCommissionFlowService;
import com.aryn.cloud.promotion.service.IDistributionConfigService;
import com.aryn.cloud.promotion.service.IDistributionOrderService;
import com.aryn.cloud.promotion.service.IDistributionRefundRecordService;
import com.aryn.cloud.promotion.service.IDistributionUserService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributionRefundServiceTest {

	@Test
	void refundRollsBackBothLevelsAndCreatesDebtForWithdrawnCommission() {
		IDistributionConfigService configService = mock(IDistributionConfigService.class);
		IDistributionOrderService orderService = mock(IDistributionOrderService.class);
		IDistributionUserService userService = mock(IDistributionUserService.class);
		IDistributionCommissionFlowService flowService = mock(IDistributionCommissionFlowService.class);
		IDistributionRefundRecordService refundRecordService = mock(IDistributionRefundRecordService.class);
		DistributionSettlementServiceImpl service = new DistributionSettlementServiceImpl(
			configService, orderService, userService, flowService, refundRecordService);

		DistributionOrder level1 = settledOrder("dist-1", "seller-1", "10.00", 1);
		DistributionOrder level2 = settledOrder("dist-2", "seller-2", "5.00", 2);
		DistributionUser seller1 = distributor("seller-1", "2.00", "10.00");
		DistributionUser seller2 = distributor("seller-2", "5.00", "5.00");
		when(orderService.getOne(any())).thenReturn(level1);
		when(orderService.list(any(Wrapper.class))).thenReturn(List.of(level1, level2));
		when(userService.getByUserId("seller-1")).thenReturn(seller1);
		when(userService.getByUserId("seller-2")).thenReturn(seller2);
		when(userService.updateById(any())).thenReturn(true);
		when(orderService.updateById(any())).thenReturn(true);
		when(flowService.save(any())).thenReturn(true);
		when(refundRecordService.save(any())).thenReturn(true);
		when(refundRecordService.updateById(any())).thenReturn(true);

		service.refundCommission("order-1", "refund-1", new BigDecimal("40.00"), new BigDecimal("40.00"));

		assertThat(level1.getRefundedBaseAmount()).isEqualByComparingTo("40.00");
		assertThat(level1.getRefundedCommissionAmount()).isEqualByComparingTo("4.00");
		assertThat(level2.getRefundedCommissionAmount()).isEqualByComparingTo("2.00");
		assertThat(seller1.getAvailableCommission()).isEqualByComparingTo("0.00");
		assertThat(seller1.getCommissionDebt()).isEqualByComparingTo("2.00");
		assertThat(seller2.getAvailableCommission()).isEqualByComparingTo("3.00");
		verify(userService).updateById(seller1);
		verify(userService).updateById(seller2);
	}

	@Test
	void cumulativeRefundIsCappedAtOriginalCommission() {
		IDistributionConfigService configService = mock(IDistributionConfigService.class);
		IDistributionOrderService orderService = mock(IDistributionOrderService.class);
		IDistributionUserService userService = mock(IDistributionUserService.class);
		IDistributionCommissionFlowService flowService = mock(IDistributionCommissionFlowService.class);
		IDistributionRefundRecordService refundRecordService = mock(IDistributionRefundRecordService.class);
		DistributionSettlementServiceImpl service = new DistributionSettlementServiceImpl(
			configService, orderService, userService, flowService, refundRecordService);

		DistributionOrder order = settledOrder("dist-1", "seller-1", "10.00", 1)
			.setRefundedBaseAmount(new BigDecimal("80.00"))
			.setRefundedCommissionAmount(new BigDecimal("8.00"));
		DistributionUser seller = distributor("seller-1", "20.00", "2.00");
		when(orderService.getOne(any())).thenReturn(order);
		when(orderService.list(any(Wrapper.class))).thenReturn(List.of(order));
		when(userService.getByUserId("seller-1")).thenReturn(seller);
		when(userService.updateById(any())).thenReturn(true);
		when(orderService.updateById(any())).thenReturn(true);
		when(flowService.save(any())).thenReturn(true);
		when(refundRecordService.save(any())).thenReturn(true);
		when(refundRecordService.updateById(any())).thenReturn(true);

		service.refundCommission("order-1", "refund-1", new BigDecimal("50.00"), new BigDecimal("50.00"));

		assertThat(order.getRefundedBaseAmount()).isEqualByComparingTo("100.00");
		assertThat(order.getRefundedCommissionAmount()).isEqualByComparingTo("10.00");
		assertThat(order.getStatus()).isEqualTo(DistributionOrderStatusEnum.STATUS_2.getCode());
		assertThat(seller.getAvailableCommission()).isEqualByComparingTo("18.00");
		ArgumentCaptor<DistributionCommissionFlow> flowCaptor =
			ArgumentCaptor.forClass(DistributionCommissionFlow.class);
		verify(flowService).save(flowCaptor.capture());
		assertThat(flowCaptor.getValue().getAmount()).isEqualByComparingTo("2.00");
	}

	@Test
	void zeroOrNegativeRefundDoesNotChangeBalances() {
		IDistributionOrderService orderService = mock(IDistributionOrderService.class);
		IDistributionUserService userService = mock(IDistributionUserService.class);
		DistributionSettlementServiceImpl service = new DistributionSettlementServiceImpl(
			mock(IDistributionConfigService.class), orderService, userService,
			mock(IDistributionCommissionFlowService.class), mock(IDistributionRefundRecordService.class));

		service.refundCommission("order-1", "refund-1", BigDecimal.ZERO, BigDecimal.ZERO);

		verify(orderService, never()).getOne(any());
		verify(userService, never()).updateById(any());
	}

	@Test
	void sameRefundNumberIsProcessedOnlyOnce() {
		IDistributionOrderService orderService = mock(IDistributionOrderService.class);
		IDistributionUserService userService = mock(IDistributionUserService.class);
		IDistributionCommissionFlowService flowService = mock(IDistributionCommissionFlowService.class);
		IDistributionRefundRecordService refundRecordService = mock(IDistributionRefundRecordService.class);
		DistributionSettlementServiceImpl service = new DistributionSettlementServiceImpl(
			mock(IDistributionConfigService.class), orderService, userService, flowService, refundRecordService);
		DistributionOrder order = settledOrder("dist-1", "seller-1", "10.00", 1);
		DistributionUser seller = distributor("seller-1", "10.00", "10.00");
		AtomicBoolean recorded = new AtomicBoolean();
		when(refundRecordService.getOne(any())).thenAnswer(invocation -> recorded.get()
			? new DistributionRefundRecord() : null);
		when(refundRecordService.save(any())).thenAnswer(invocation -> recorded.compareAndSet(false, true));
		when(refundRecordService.updateById(any())).thenReturn(true);
		when(orderService.getOne(any())).thenReturn(order);
		when(orderService.list(any(Wrapper.class))).thenReturn(List.of(order));
		when(userService.getByUserId("seller-1")).thenReturn(seller);
		when(userService.updateById(any())).thenReturn(true);
		when(orderService.updateById(any())).thenReturn(true);
		when(flowService.save(any())).thenReturn(true);

		service.refundCommission("order-1", "refund-1", new BigDecimal("20.00"), new BigDecimal("20.00"));
		service.refundCommission("order-1", "refund-1", new BigDecimal("20.00"), new BigDecimal("20.00"));

		verify(orderService, org.mockito.Mockito.times(1)).list(any(Wrapper.class));
		assertThat(order.getRefundedCommissionAmount()).isEqualByComparingTo("2.00");
	}

	@Test
	void smallRefundsAccumulateBeforeCommissionDeduction() {
		IDistributionOrderService orderService = mock(IDistributionOrderService.class);
		IDistributionUserService userService = mock(IDistributionUserService.class);
		IDistributionCommissionFlowService flowService = mock(IDistributionCommissionFlowService.class);
		IDistributionRefundRecordService refundRecordService = mock(IDistributionRefundRecordService.class);
		DistributionSettlementServiceImpl service = new DistributionSettlementServiceImpl(
			mock(IDistributionConfigService.class), orderService, userService, flowService, refundRecordService);
		DistributionOrder order = settledOrder("dist-1", "seller-1", "1.00", 1);
		DistributionUser seller = distributor("seller-1", "1.00", "1.00");
		when(refundRecordService.save(any())).thenReturn(true);
		when(refundRecordService.updateById(any())).thenReturn(true);
		when(orderService.list(any(Wrapper.class))).thenReturn(List.of(order));
		when(userService.getByUserId("seller-1")).thenReturn(seller);
		when(userService.updateById(any())).thenReturn(true);
		when(orderService.updateById(any())).thenReturn(true);
		when(flowService.save(any())).thenReturn(true);

		service.refundCommission("order-1", "refund-1", new BigDecimal("0.50"), new BigDecimal("0.50"));
		service.refundCommission("order-1", "refund-2", new BigDecimal("0.50"), new BigDecimal("0.50"));

		assertThat(order.getRefundedBaseAmount()).isEqualByComparingTo("1.00");
		assertThat(order.getRefundedCommissionAmount()).isEqualByComparingTo("0.01");
		assertThat(seller.getAvailableCommission()).isEqualByComparingTo("0.99");
	}

	@Test
	void legacyFreightInclusiveOrderUsesGrossRefundForFullRollback() throws Exception {
		IDistributionOrderService orderService = mock(IDistributionOrderService.class);
		IDistributionUserService userService = mock(IDistributionUserService.class);
		IDistributionCommissionFlowService flowService = mock(IDistributionCommissionFlowService.class);
		IDistributionRefundRecordService refundRecordService = mock(IDistributionRefundRecordService.class);
		DistributionSettlementServiceImpl service = new DistributionSettlementServiceImpl(
			mock(IDistributionConfigService.class), orderService, userService, flowService, refundRecordService);
		DistributionOrder legacyOrder = settledOrder("dist-1", "seller-1", "11.00", 1)
			.setOrderAmount(new BigDecimal("110.00"))
			.setCommissionBaseAmount(null);
		DistributionUser seller = distributor("seller-1", "11.00", "11.00");
		when(refundRecordService.save(any())).thenReturn(true);
		when(refundRecordService.updateById(any())).thenReturn(true);
		when(orderService.list(any(Wrapper.class))).thenReturn(List.of(legacyOrder));
		when(userService.getByUserId("seller-1")).thenReturn(seller);
		when(userService.updateById(any())).thenReturn(true);
		when(orderService.updateById(any())).thenReturn(true);
		when(flowService.save(any())).thenReturn(true);
		Method method = service.getClass().getMethod("refundCommission", String.class, String.class,
			BigDecimal.class, BigDecimal.class);

		method.invoke(service, "order-1", "refund-1", new BigDecimal("110.00"), new BigDecimal("100.00"));

		assertThat(legacyOrder.getRefundedCommissionAmount()).isEqualByComparingTo("11.00");
		assertThat(legacyOrder.getStatus()).isEqualTo(DistributionOrderStatusEnum.STATUS_2.getCode());
		assertThat(seller.getAvailableCommission()).isEqualByComparingTo("0.00");
	}

	@Test
	void pendingRefundCanBeReplayedAfterConcurrentPaymentWindow() {
		IDistributionOrderService orderService = mock(IDistributionOrderService.class);
		IDistributionUserService userService = mock(IDistributionUserService.class);
		IDistributionCommissionFlowService flowService = mock(IDistributionCommissionFlowService.class);
		IDistributionRefundRecordService refundRecordService = mock(IDistributionRefundRecordService.class);
		DistributionSettlementServiceImpl service = new DistributionSettlementServiceImpl(
			mock(IDistributionConfigService.class), orderService, userService, flowService, refundRecordService);
		DistributionRefundRecord record = new DistributionRefundRecord();
		record.setId("refund-record-1");
		record.setRefundNo("refund-1");
		record.setBizOrderId("order-1");
		record.setRefundAmount(new BigDecimal("20.00"));
		record.setRefundBaseAmount(new BigDecimal("20.00"));
		record.setApplied("0");
		DistributionOrder order = settledOrder("dist-1", "seller-1", "10.00", 1);
		DistributionUser seller = distributor("seller-1", "10.00", "10.00");
		when(refundRecordService.getById("refund-record-1")).thenReturn(record);
		when(refundRecordService.updateById(record)).thenReturn(true);
		when(orderService.list(any(Wrapper.class))).thenReturn(List.of(order));
		when(userService.getByUserId("seller-1")).thenReturn(seller);
		when(userService.updateById(any())).thenReturn(true);
		when(orderService.updateById(any())).thenReturn(true);
		when(flowService.save(any())).thenReturn(true);

		Boolean applied = service.replayPendingRefund("refund-record-1");

		assertThat(applied).isTrue();
		assertThat(record.getApplied()).isEqualTo("1");
		assertThat(record.getAppliedTime()).isNotNull();
		assertThat(order.getRefundedCommissionAmount()).isEqualByComparingTo("2.00");
	}

	@Test
	void duplicateRefundMessageRetriesAnUnappliedRecord() {
		IDistributionOrderService orderService = mock(IDistributionOrderService.class);
		IDistributionUserService userService = mock(IDistributionUserService.class);
		IDistributionCommissionFlowService flowService = mock(IDistributionCommissionFlowService.class);
		IDistributionRefundRecordService refundRecordService = mock(IDistributionRefundRecordService.class);
		DistributionSettlementServiceImpl service = new DistributionSettlementServiceImpl(
			mock(IDistributionConfigService.class), orderService, userService, flowService, refundRecordService);
		DistributionRefundRecord record = new DistributionRefundRecord();
		record.setId("refund-record-1");
		record.setRefundNo("refund-1");
		record.setBizOrderId("order-1");
		record.setRefundAmount(new BigDecimal("20.00"));
		record.setRefundBaseAmount(new BigDecimal("20.00"));
		record.setApplied("0");
		DistributionOrder order = settledOrder("dist-1", "seller-1", "10.00", 1);
		DistributionUser seller = distributor("seller-1", "10.00", "10.00");
		when(refundRecordService.getOne(any())).thenReturn(record);
		when(refundRecordService.updateById(record)).thenReturn(true);
		when(orderService.list(any(Wrapper.class))).thenReturn(List.of(order));
		when(userService.getByUserId("seller-1")).thenReturn(seller);
		when(userService.updateById(any())).thenReturn(true);
		when(orderService.updateById(any())).thenReturn(true);
		when(flowService.save(any())).thenReturn(true);

		Boolean result = service.refundCommission(
			"order-1", "refund-1", new BigDecimal("20.00"), new BigDecimal("20.00"));

		assertThat(result).isTrue();
		assertThat(record.getApplied()).isEqualTo("1");
	}

	private DistributionOrder settledOrder(String id, String userId, String commission, int level) {
		return new DistributionOrder()
			.setId(id)
			.setBizOrderId("order-1")
			.setDistributorUserId(userId)
			.setOrderAmount(new BigDecimal("100.00"))
			.setCommissionBaseAmount(new BigDecimal("100.00"))
			.setCommissionAmount(new BigDecimal(commission))
			.setRefundedBaseAmount(BigDecimal.ZERO)
			.setRefundedCommissionAmount(BigDecimal.ZERO)
			.setCommissionLevel(level)
			.setStatus(DistributionOrderStatusEnum.STATUS_1.getCode());
	}

	private DistributionUser distributor(String userId, String available, String total) {
		return new DistributionUser()
			.setUserId(userId)
			.setAvailableCommission(new BigDecimal(available))
			.setPendingCommission(BigDecimal.ZERO)
			.setTotalCommission(new BigDecimal(total))
			.setCommissionDebt(BigDecimal.ZERO);
	}
}
