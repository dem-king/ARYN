package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.SalesPayoutConfirmDTO;
import com.aryn.cloud.promotion.api.entity.SalesPayout;
import com.aryn.cloud.promotion.api.entity.SalesPayoutAdjustment;
import com.aryn.cloud.promotion.mapper.SalesPayoutMapper;
import com.aryn.cloud.promotion.service.ISalesOrderService;
import com.aryn.cloud.promotion.service.ISalesPayoutAdjustmentService;
import com.aryn.cloud.promotion.vo.SalesPayoutAggregateVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 月度发放台账单测：月份归属、生成幂等、负数结转、仅调整销售、入批后按实际批次重算、退款重算、确认发放联动
 */
class SalesPayoutServiceImplTest {

	private static final String PAYOUT_ID = "payout-new";

	private static final LocalDateTime PAY_TIME_BEFORE = LocalDateTime.of(2026, 11, 1, 0, 0);

	private ISalesOrderService salesOrderService;

	private ISalesPayoutAdjustmentService adjustmentService;

	private SalesPayoutMapper mapper;

	private SalesPayoutServiceImpl service;

	private final AtomicReference<SalesPayout> savedPayout = new AtomicReference<>();

	@BeforeEach
	void setUp() {
		salesOrderService = mock(ISalesOrderService.class);
		adjustmentService = mock(ISalesPayoutAdjustmentService.class);
		mapper = mock(SalesPayoutMapper.class);
		service = new SalesPayoutServiceImpl(salesOrderService, adjustmentService);
		ReflectionTestUtils.setField(service, "baseMapper", mapper);
		// 模拟 MyBatis-Plus 主键生成：insert 回填 id，createPayout 入批后才能按实际批次重算
		when(mapper.insert(any(SalesPayout.class))).thenAnswer(invocation -> {
			SalesPayout payout = invocation.getArgument(0);
			payout.setId(PAYOUT_ID);
			savedPayout.set(payout);
			return 1;
		});
		when(mapper.selectById(PAYOUT_ID)).thenAnswer(invocation -> savedPayout.get());
		when(mapper.updateById(any(SalesPayout.class))).thenReturn(1);
	}

	@Test
	void generatesPayoutWithAdjustmentDeductionAndAssignsOrders() {
		when(salesOrderService.aggregatePayoutOrders(any())).thenReturn(List.of(aggregate()));
		when(mapper.selectCount(any())).thenReturn(0L);
		SalesPayoutAdjustment adjustment = new SalesPayoutAdjustment().setId("adj-1")
			.setSalesStaffId("staff-1")
			.setAmount(new BigDecimal("-3.00"));
		when(adjustmentService.listPending(PAY_TIME_BEFORE)).thenReturn(List.of(adjustment));
		when(adjustmentService.listPendingByStaff("staff-1", PAY_TIME_BEFORE)).thenReturn(List.of(adjustment));
		when(salesOrderService.assignPayout(anyString(), any(), any())).thenReturn(2);
		when(adjustmentService.settleAdjustments(any(), any())).thenReturn(true);
		when(salesOrderService.summarizePayoutOrders(PAYOUT_ID)).thenReturn(summary(2, "200.00", "10.00"));

		int generated = service.generate("2026-10");

		assertThat(generated).isEqualTo(1);
		ArgumentCaptor<SalesPayout> captor = ArgumentCaptor.forClass(SalesPayout.class);
		verify(mapper).insert(captor.capture());
		SalesPayout payout = captor.getValue();
		assertThat(payout.getPayoutMonth()).isEqualTo("2026-10");
		assertThat(payout.getOrderCount()).isEqualTo(2);
		assertThat(payout.getTradeAmount()).isEqualByComparingTo("200.00");
		assertThat(payout.getCommissionAmount()).isEqualByComparingTo("10.00");
		assertThat(payout.getRefundDeductAmount()).isEqualByComparingTo("3.00");
		assertThat(payout.getPayableAmount()).isEqualByComparingTo("7.00");
		assertThat(payout.getStatus()).isEqualTo("0");
		// 台账月次月 1 日 0 点是支付时间/负数调整创建时间的截断点：本月订单和之后产生的调整都不会进上月台账
		ArgumentCaptor<LocalDateTime> payTimeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(salesOrderService).aggregatePayoutOrders(payTimeCaptor.capture());
		assertThat(payTimeCaptor.getValue()).isEqualTo(PAY_TIME_BEFORE);
		verify(salesOrderService).assignPayout(eq("staff-1"), any(), eq(PAY_TIME_BEFORE));
		verify(adjustmentService).listPending(PAY_TIME_BEFORE);
		verify(adjustmentService).listPendingByStaff("staff-1", PAY_TIME_BEFORE);
		verify(adjustmentService).settleAdjustments(eq(List.of(adjustment)), any());
		// 入批/结转完成后按实际批次重算一次，台账金额与批次明细一致
		verify(salesOrderService).summarizePayoutOrders(PAYOUT_ID);
		verify(mapper).updateById(any(SalesPayout.class));
	}

	@Test
	void generatingSameMonthTwiceIsIdempotent() {
		when(salesOrderService.aggregatePayoutOrders(any())).thenReturn(List.of(aggregate()));
		when(mapper.selectCount(any())).thenReturn(1L);

		int generated = service.generate("2026-10");

		assertThat(generated).isZero();
		verify(mapper, never()).insert(any(SalesPayout.class));
		verify(salesOrderService, never()).assignPayout(anyString(), any(), any());
		verify(salesOrderService, never()).summarizePayoutOrders(any());
	}

	@Test
	void crossMonthUnpaidOrdersRollIntoNextBatch() {
		// 上月未发放的业绩单（status=1 且 payout_id 为空）由聚合查询带出，自然顺延到本期批次
		when(salesOrderService.aggregatePayoutOrders(any())).thenReturn(List.of(aggregate()));
		when(adjustmentService.listPending(any())).thenReturn(List.of());
		when(adjustmentService.listPendingByStaff(anyString(), any())).thenReturn(List.of());
		when(mapper.selectCount(any())).thenReturn(0L);
		when(salesOrderService.assignPayout(anyString(), any(), any())).thenReturn(1);
		when(adjustmentService.settleAdjustments(any(), any())).thenReturn(true);
		when(salesOrderService.summarizePayoutOrders(PAYOUT_ID)).thenReturn(summary(2, "200.00", "10.00"));

		assertThat(service.generate(null)).isEqualTo(1);

		ArgumentCaptor<LocalDateTime> payTimeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
		verify(salesOrderService).aggregatePayoutOrders(payTimeCaptor.capture());
		assertThat(payTimeCaptor.getValue()).isEqualTo(YearMonth.now().atDay(1).atStartOfDay());
		ArgumentCaptor<SalesPayout> captor = ArgumentCaptor.forClass(SalesPayout.class);
		verify(mapper).insert(captor.capture());
		assertThat(captor.getValue().getPayoutMonth()).isEqualTo(YearMonth.now().minusMonths(1).toString());
	}

	@Test
	void adjustmentOnlyStaffGeneratesNegativePayout() {
		// 只有退款冲减、没有可发放业绩单的销售（含离职销售）也要生成台账，否则扣回落不了地
		when(salesOrderService.aggregatePayoutOrders(any())).thenReturn(List.of());
		SalesPayoutAdjustment adjustment = new SalesPayoutAdjustment().setId("adj-9")
			.setSalesStaffId("staff-9")
			.setStaffName("王五")
			.setAmount(new BigDecimal("-6.00"));
		when(adjustmentService.listPending(PAY_TIME_BEFORE)).thenReturn(List.of(adjustment));
		when(adjustmentService.listPendingByStaff("staff-9", PAY_TIME_BEFORE)).thenReturn(List.of(adjustment));
		when(mapper.selectCount(any())).thenReturn(0L);
		when(salesOrderService.assignPayout(anyString(), any(), any())).thenReturn(0);
		when(adjustmentService.settleAdjustments(any(), any())).thenReturn(true);
		when(salesOrderService.summarizePayoutOrders(PAYOUT_ID)).thenReturn(summary(0, "0", "0"));

		assertThat(service.generate("2026-10")).isEqualTo(1);

		ArgumentCaptor<SalesPayout> captor = ArgumentCaptor.forClass(SalesPayout.class);
		verify(mapper).insert(captor.capture());
		SalesPayout payout = captor.getValue();
		assertThat(payout.getSalesStaffId()).isEqualTo("staff-9");
		assertThat(payout.getStaffName()).isEqualTo("王五");
		assertThat(payout.getOrderCount()).isZero();
		assertThat(payout.getTradeAmount()).isEqualByComparingTo("0");
		assertThat(payout.getCommissionAmount()).isEqualByComparingTo("0");
		assertThat(payout.getRefundDeductAmount()).isEqualByComparingTo("6.00");
		// 允许负数：实发 = 0 - 冲减
		assertThat(payout.getPayableAmount()).isEqualByComparingTo("-6.00");
		verify(adjustmentService).settleAdjustments(eq(List.of(adjustment)), any());
	}

	@Test
	void generatesPayoutRecalculatedFromActualBatchedOrders() {
		// 聚合到入批之间发生并发退款：聚合 2 单实际只入批 1 单，台账必须按实际批次重算
		when(salesOrderService.aggregatePayoutOrders(any())).thenReturn(List.of(aggregate()));
		when(adjustmentService.listPending(any())).thenReturn(List.of());
		when(adjustmentService.listPendingByStaff(anyString(), any())).thenReturn(List.of());
		when(mapper.selectCount(any())).thenReturn(0L);
		when(salesOrderService.assignPayout(anyString(), any(), any())).thenReturn(1);
		when(salesOrderService.summarizePayoutOrders(PAYOUT_ID)).thenReturn(summary(1, "100.00", "5.00"));

		assertThat(service.generate("2026-10")).isEqualTo(1);

		ArgumentCaptor<SalesPayout> captor = ArgumentCaptor.forClass(SalesPayout.class);
		verify(mapper).insert(captor.capture());
		SalesPayout payout = captor.getValue();
		assertThat(payout.getOrderCount()).isEqualTo(1);
		assertThat(payout.getTradeAmount()).isEqualByComparingTo("100.00");
		assertThat(payout.getCommissionAmount()).isEqualByComparingTo("5.00");
		assertThat(payout.getPayableAmount()).isEqualByComparingTo("5.00");
		verify(mapper).updateById(payout);
	}

	@Test
	void pendingAdjustmentCreatedAfterPayoutMonthEndIsExcluded() {
		// 创建时间 >= 台账月末（次月 1 日 0 点）的负数调整不进本期台账，留到下期
		when(salesOrderService.aggregatePayoutOrders(any())).thenReturn(List.of());
		when(adjustmentService.listPending(PAY_TIME_BEFORE)).thenReturn(List.of());

		assertThat(service.generate("2026-10")).isZero();

		verify(adjustmentService).listPending(PAY_TIME_BEFORE);
		verify(mapper, never()).insert(any(SalesPayout.class));
	}

	@Test
	void recalcPendingPayoutRecomputesAmountsAfterRefund() {
		SalesPayout payout = new SalesPayout().setId("payout-1")
			.setStatus("0")
			.setSalesStaffId("staff-1")
			.setOrderCount(2)
			.setTradeAmount(new BigDecimal("200.00"))
			.setCommissionAmount(new BigDecimal("10.00"))
			.setRefundDeductAmount(new BigDecimal("1.00"))
			.setPayableAmount(new BigDecimal("9.00"));
		when(mapper.selectById("payout-1")).thenReturn(payout);
		when(salesOrderService.summarizePayoutOrders("payout-1")).thenReturn(summary(1, "100.00", "5.00"));

		assertThat(service.recalcPendingPayout("payout-1")).isTrue();

		assertThat(payout.getOrderCount()).isEqualTo(1);
		assertThat(payout.getTradeAmount()).isEqualByComparingTo("100.00");
		assertThat(payout.getCommissionAmount()).isEqualByComparingTo("5.00");
		// 上期退款冲减金额不变，实发金额按重算后的应发提成重算
		assertThat(payout.getRefundDeductAmount()).isEqualByComparingTo("1.00");
		assertThat(payout.getPayableAmount()).isEqualByComparingTo("4.00");
		verify(mapper).updateById(payout);
	}

	@Test
	void recalcPendingPayoutSkipsNonPendingPayout() {
		when(mapper.selectById("payout-1"))
			.thenReturn(new SalesPayout().setId("payout-1").setStatus("1"));
		when(mapper.selectById("missing")).thenReturn(null);

		assertThat(service.recalcPendingPayout("payout-1")).isFalse();
		assertThat(service.recalcPendingPayout("missing")).isFalse();
		assertThat(service.recalcPendingPayout(null)).isFalse();

		verify(salesOrderService, never()).summarizePayoutOrders(any());
		verify(mapper, never()).updateById(any(SalesPayout.class));
	}

	@Test
	void confirmLocksOrdersThenRecalculatesAndUsesLatestPayout() {
		// 首次读取的旧台账（非锁定校验用）
		SalesPayout initial = new SalesPayout().setId("payout-1")
			.setStatus("0")
			.setSalesStaffId("staff-1")
			.setOrderCount(2)
			.setTradeAmount(new BigDecimal("200.00"))
			.setCommissionAmount(new BigDecimal("10.00"))
			.setRefundDeductAmount(BigDecimal.ZERO)
			.setPayableAmount(new BigDecimal("10.00"));
		// recalc 自己读取的台账（重算后写回退款后金额）
		SalesPayout recalcLoaded = new SalesPayout().setId("payout-1")
			.setStatus("0")
			.setSalesStaffId("staff-1")
			.setOrderCount(2)
			.setTradeAmount(new BigDecimal("200.00"))
			.setCommissionAmount(new BigDecimal("10.00"))
			.setRefundDeductAmount(BigDecimal.ZERO)
			.setPayableAmount(new BigDecimal("10.00"));
		// recalc 之后重新读取的最新台账（模拟数据库已更新，退款冲减 5.00）
		SalesPayout refreshed = new SalesPayout().setId("payout-1")
			.setStatus("0")
			.setSalesStaffId("staff-1")
			.setOrderCount(1)
			.setTradeAmount(new BigDecimal("100.00"))
			.setCommissionAmount(new BigDecimal("5.00"))
			.setRefundDeductAmount(BigDecimal.ZERO)
			.setPayableAmount(new BigDecimal("5.00"));
		when(mapper.selectById("payout-1")).thenReturn(initial, recalcLoaded, refreshed);
		when(salesOrderService.summarizePayoutOrders("payout-1")).thenReturn(summary(1, "100.00", "5.00"));
		when(salesOrderService.markPayoutPaid(eq("payout-1"), any())).thenReturn(1);
		SalesPayoutConfirmDTO dto = new SalesPayoutConfirmDTO();
		dto.setId("payout-1");
		dto.setRemark("凭证 20261105");

		assertThat(service.confirm(dto)).isTrue();

		// 锁序与退款一致：先锁批次内订单（markPayoutPaid），再重算台账（recalc 锁 payout 行），最后回写
		InOrder inOrder = inOrder(salesOrderService, mapper);
		inOrder.verify(salesOrderService).markPayoutPaid(eq("payout-1"), any());
		inOrder.verify(salesOrderService).summarizePayoutOrders("payout-1");
		inOrder.verify(mapper, times(2)).updateById(any(SalesPayout.class));

		ArgumentCaptor<SalesPayout> captor = ArgumentCaptor.forClass(SalesPayout.class);
		verify(mapper, times(2)).updateById(captor.capture());
		assertThat(captor.getAllValues().get(0).getPayableAmount()).isEqualByComparingTo("5.00");
		// 最后回写必须用 recalc 后重新读取的台账，不能把重算金额覆盖回旧值
		SalesPayout confirmed = captor.getAllValues().get(1);
		assertThat(confirmed).isSameAs(refreshed);
		assertThat(confirmed.getStatus()).isEqualTo("1");
		assertThat(confirmed.getPayableAmount()).isEqualByComparingTo("5.00");
		assertThat(confirmed.getRemark()).isEqualTo("凭证 20261105");
		assertThat(confirmed.getConfirmTime()).isNotNull();
	}

	@Test
	void confirmMarksPayoutPaidAndLinksOrders() {
		SalesPayout payout = new SalesPayout().setId("payout-1")
			.setStatus("0")
			.setSalesStaffId("staff-1")
			.setCommissionAmount(new BigDecimal("10.00"))
			.setRefundDeductAmount(BigDecimal.ZERO)
			.setPayableAmount(new BigDecimal("10.00"));
		when(mapper.selectById("payout-1")).thenReturn(payout);
		when(salesOrderService.summarizePayoutOrders("payout-1")).thenReturn(summary(2, "200.00", "10.00"));
		when(salesOrderService.markPayoutPaid(any(), any())).thenReturn(2);
		SalesPayoutConfirmDTO dto = new SalesPayoutConfirmDTO();
		dto.setId("payout-1");
		dto.setRemark("凭证 20261105");

		assertThat(service.confirm(dto)).isTrue();

		assertThat(payout.getStatus()).isEqualTo("1");
		assertThat(payout.getOrderCount()).isEqualTo(2);
		assertThat(payout.getConfirmTime()).isNotNull();
		assertThat(payout.getRemark()).isEqualTo("凭证 20261105");
		verify(salesOrderService).markPayoutPaid(eq("payout-1"), any());
	}

	@Test
	void confirmThrowsAndRollsBackWhenBatchedOrdersChanged() {
		// recalc 时批次内 2 单可发放，markPayoutPaid 只更新到 1 单 → 有单被并发退款，必须回滚而不是按旧金额发放
		SalesPayout payout = new SalesPayout().setId("payout-1")
			.setStatus("0")
			.setSalesStaffId("staff-1")
			.setCommissionAmount(new BigDecimal("10.00"))
			.setRefundDeductAmount(BigDecimal.ZERO)
			.setPayableAmount(new BigDecimal("10.00"));
		when(mapper.selectById("payout-1")).thenReturn(payout);
		when(salesOrderService.summarizePayoutOrders("payout-1")).thenReturn(summary(2, "200.00", "10.00"));
		when(salesOrderService.markPayoutPaid(any(), any())).thenReturn(1);
		SalesPayoutConfirmDTO dto = new SalesPayoutConfirmDTO();
		dto.setId("payout-1");

		assertThatThrownBy(() -> service.confirm(dto)).isInstanceOf(ArynBusinessException.class)
			.satisfies(ex -> assertThat(((ArynBusinessException) ex).getMsg()).contains("请刷新后重试"));
		verify(salesOrderService).markPayoutPaid(eq("payout-1"), any());
	}

	@Test
	void confirmAdjustmentOnlyPayoutWithZeroOrdersSucceeds() {
		// 只有退款冲减的负实发台账：orderCount=0、paidOrders=0，数量校验一致不应误报
		SalesPayout payout = new SalesPayout().setId("payout-1")
			.setStatus("0")
			.setSalesStaffId("staff-9")
			.setCommissionAmount(BigDecimal.ZERO)
			.setRefundDeductAmount(new BigDecimal("6.00"))
			.setPayableAmount(new BigDecimal("-6.00"));
		when(mapper.selectById("payout-1")).thenReturn(payout);
		when(salesOrderService.summarizePayoutOrders("payout-1")).thenReturn(summary(0, "0", "0"));
		when(salesOrderService.markPayoutPaid(any(), any())).thenReturn(0);
		SalesPayoutConfirmDTO dto = new SalesPayoutConfirmDTO();
		dto.setId("payout-1");

		assertThat(service.confirm(dto)).isTrue();

		assertThat(payout.getStatus()).isEqualTo("1");
		assertThat(payout.getPayableAmount()).isEqualByComparingTo("-6.00");
		verify(salesOrderService).markPayoutPaid(eq("payout-1"), any());
	}

	@Test
	void confirmRejectsAlreadyPaidPayout() {
		when(mapper.selectById("payout-1")).thenReturn(new SalesPayout().setId("payout-1").setStatus("1"));
		SalesPayoutConfirmDTO dto = new SalesPayoutConfirmDTO();
		dto.setId("payout-1");

		assertThatThrownBy(() -> service.confirm(dto)).isInstanceOf(ArynBusinessException.class);
		verify(salesOrderService, never()).summarizePayoutOrders(any());
		verify(salesOrderService, never()).markPayoutPaid(any(), any());
	}

	@Test
	void rejectsCurrentMonthAndFutureMonthGeneration() {
		// 未到月末不能提前生成当月台账；更不能把当前未入批订单装进未来月份台账
		assertThatThrownBy(() -> service.generate(YearMonth.now().toString()))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(ex -> assertThat(((ArynBusinessException) ex).getMsg()).contains("只能生成上月及更早月份"));
		assertThatThrownBy(() -> service.generate(YearMonth.now().plusMonths(1).toString()))
			.isInstanceOf(ArynBusinessException.class)
			.satisfies(ex -> assertThat(((ArynBusinessException) ex).getMsg()).contains("只能生成上月及更早月份"));
		verify(salesOrderService, never()).aggregatePayoutOrders(any());
	}

	@Test
	void allowsLastMonthAndEarlierMonthGeneration() {
		when(salesOrderService.aggregatePayoutOrders(any())).thenReturn(List.of());
		when(adjustmentService.listPending(any())).thenReturn(List.of());

		assertThat(service.generate(YearMonth.now().minusMonths(1).toString())).isZero();
		assertThat(service.generate(YearMonth.now().minusMonths(2).toString())).isZero();
	}

	@Test
	void settledOrdersAreExcludedFromAggregation() {
		// 聚合由 SQL 保证：status=1 且 payout_id 为空；已入批次的订单不会重复计入
		when(salesOrderService.aggregatePayoutOrders(any())).thenReturn(List.of());
		when(adjustmentService.listPending(any())).thenReturn(List.of());

		assertThat(service.generate("2026-10")).isZero();
		verify(mapper, never()).insert(any(SalesPayout.class));
	}

	private SalesPayoutAggregateVO aggregate() {
		SalesPayoutAggregateVO aggregate = new SalesPayoutAggregateVO();
		aggregate.setSalesStaffId("staff-1");
		aggregate.setStaffName("李四");
		aggregate.setDeptId("dept-1");
		aggregate.setOrderCount(2);
		aggregate.setTradeAmount(new BigDecimal("200.00"));
		aggregate.setCommissionAmount(new BigDecimal("10.00"));
		return aggregate;
	}

	private SalesPayoutAggregateVO summary(int orderCount, String tradeAmount, String commissionAmount) {
		SalesPayoutAggregateVO summary = new SalesPayoutAggregateVO();
		summary.setOrderCount(orderCount);
		summary.setTradeAmount(new BigDecimal(tradeAmount));
		summary.setCommissionAmount(new BigDecimal(commissionAmount));
		return summary;
	}

}
