package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.promotion.api.constant.MallEventConstants;
import com.aryn.cloud.promotion.api.dto.DistributionSettleDTO;
import com.aryn.cloud.promotion.api.entity.DistributionConfig;
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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.baomidou.mybatisplus.core.conditions.Wrapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributionSettlementServiceImplTest {

	@Test
	void refundRecordedBeforePaymentIsAppliedAfterCommissionOrderCreation() {
		IDistributionConfigService configService = mock(IDistributionConfigService.class);
		IDistributionOrderService orderService = mock(IDistributionOrderService.class);
		IDistributionUserService userService = mock(IDistributionUserService.class);
		IDistributionCommissionFlowService flowService = mock(IDistributionCommissionFlowService.class);
		IDistributionRefundRecordService refundRecordService = mock(IDistributionRefundRecordService.class);
		DistributionSettlementServiceImpl service = new DistributionSettlementServiceImpl(
			configService, orderService, userService, flowService, refundRecordService);
		DistributionConfig config = new DistributionConfig()
			.setCommissionRate(new BigDecimal("0.10"))
			.setCommissionRateLevel2(BigDecimal.ZERO)
			.setSettleCycleDays(7);
		DistributionUser buyer = new DistributionUser().setUserId("buyer-1").setInviterUserId("seller-1");
		DistributionUser distributor = new DistributionUser()
			.setUserId("seller-1")
			.setStatus(MallEventConstants.DISTRIBUTION_USER_STATUS_ENABLE)
			.setTotalCommission(BigDecimal.ZERO)
			.setAvailableCommission(BigDecimal.ZERO)
			.setPendingCommission(BigDecimal.ZERO)
			.setCommissionDebt(BigDecimal.ZERO);
		DistributionRefundRecord recordedRefund = new DistributionRefundRecord();
		recordedRefund.setRefundNo("refund-1");
		recordedRefund.setBizOrderId("order-1");
		recordedRefund.setRefundBaseAmount(new BigDecimal("20.00"));
		recordedRefund.setApplied("0");
		AtomicReference<DistributionOrder> savedOrder = new AtomicReference<>();
		when(configService.getActiveConfig()).thenReturn(config);
		when(userService.getByUserId("buyer-1")).thenReturn(buyer);
		when(userService.getByUserId("seller-1")).thenReturn(distributor);
		when(orderService.save(any())).thenAnswer(invocation -> {
			savedOrder.set(invocation.getArgument(0));
			return true;
		});
		when(orderService.list(any(Wrapper.class))).thenAnswer(invocation -> List.of(savedOrder.get()));
		when(refundRecordService.list(any(Wrapper.class))).thenReturn(List.of(recordedRefund));
		when(refundRecordService.updateById(recordedRefund)).thenReturn(true);
		when(userService.updateById(any())).thenReturn(true);
		when(orderService.updateById(any())).thenReturn(true);
		when(flowService.save(any())).thenReturn(true);

		DistributionSettleDTO dto = new DistributionSettleDTO();
		dto.setOrderId("order-1");
		dto.setBuyerUserId("buyer-1");
		dto.setOrderAmount(new BigDecimal("100.00"));
		service.settleOrder(dto);

		assertThat(savedOrder.get().getRefundedBaseAmount()).isEqualByComparingTo("20.00");
		assertThat(savedOrder.get().getRefundedCommissionAmount()).isEqualByComparingTo("2.00");
		assertThat(distributor.getPendingCommission()).isEqualByComparingTo("8.00");
		assertThat(recordedRefund.getApplied()).isEqualTo("1");
	}

	@Test
	void settlingPendingOrderReleasesCommissionAfterDebtOffset() throws Exception {
		IDistributionConfigService configService = mock(IDistributionConfigService.class);
		IDistributionOrderService orderService = mock(IDistributionOrderService.class);
		IDistributionUserService userService = mock(IDistributionUserService.class);
		IDistributionCommissionFlowService flowService = mock(IDistributionCommissionFlowService.class);
		DistributionSettlementServiceImpl service = new DistributionSettlementServiceImpl(
				configService, orderService, userService, flowService, mock(IDistributionRefundRecordService.class));

		DistributionOrder order = new DistributionOrder()
				.setId("dist-order-1")
				.setBizOrderId("order-1")
				.setDistributorUserId("seller-1")
				.setCommissionAmount(new BigDecimal("10.00"))
				.setRefundedCommissionAmount(BigDecimal.ZERO)
				.setStatus(DistributionOrderStatusEnum.STATUS_0.getCode());
		DistributionUser distributor = new DistributionUser()
				.setUserId("seller-1")
				.setPendingCommission(new BigDecimal("10.00"))
				.setAvailableCommission(new BigDecimal("5.00"))
				.setTotalCommission(BigDecimal.ZERO)
				.setCommissionDebt(new BigDecimal("3.00"));
		when(orderService.getById("dist-order-1")).thenReturn(order);
		when(userService.getByUserId("seller-1")).thenReturn(distributor);
		when(orderService.updateById(any())).thenReturn(true);
		when(userService.updateById(any())).thenReturn(true);
		when(flowService.save(any())).thenReturn(true);

		Method method = Arrays.stream(service.getClass().getMethods())
				.filter(candidate -> candidate.getName().equals("settlePendingOrder"))
				.findFirst()
				.orElseThrow(() -> new AssertionError("缺少受控待结算释放入口"));
		Object result = method.invoke(service, "dist-order-1");

		assertThat(result).isEqualTo(Boolean.TRUE);
		assertThat(order.getStatus()).isEqualTo(DistributionOrderStatusEnum.STATUS_1.getCode());
		assertThat(distributor.getPendingCommission()).isEqualByComparingTo("0.00");
		assertThat(distributor.getCommissionDebt()).isEqualByComparingTo("0.00");
		assertThat(distributor.getAvailableCommission()).isEqualByComparingTo("12.00");
		assertThat(distributor.getTotalCommission()).isEqualByComparingTo("10.00");
	}

	@Test
	void paidOrderCreatesPendingCommissionInsteadOfAvailableCommission() {
		IDistributionConfigService configService = mock(IDistributionConfigService.class);
		IDistributionOrderService orderService = mock(IDistributionOrderService.class);
		IDistributionUserService userService = mock(IDistributionUserService.class);
		IDistributionCommissionFlowService flowService = mock(IDistributionCommissionFlowService.class);
		DistributionSettlementServiceImpl service = new DistributionSettlementServiceImpl(
				configService, orderService, userService, flowService, mock(IDistributionRefundRecordService.class));

		DistributionConfig config = new DistributionConfig()
				.setCommissionRate(new BigDecimal("0.10"))
				.setCommissionRateLevel2(BigDecimal.ZERO)
				.setSettleCycleDays(7);
		DistributionUser buyer = new DistributionUser().setUserId("buyer-1").setInviterUserId("seller-1");
		DistributionUser distributor = new DistributionUser()
				.setUserId("seller-1")
				.setStatus(MallEventConstants.DISTRIBUTION_USER_STATUS_ENABLE)
				.setTotalCommission(BigDecimal.ZERO)
				.setAvailableCommission(BigDecimal.ZERO)
				.setPendingCommission(BigDecimal.ZERO)
				.setCommissionDebt(BigDecimal.ZERO);
		when(configService.getActiveConfig()).thenReturn(config);
		when(userService.getByUserId("buyer-1")).thenReturn(buyer);
		when(userService.getByUserId("seller-1")).thenReturn(distributor);
		when(orderService.save(any())).thenReturn(true);
		when(userService.updateById(any())).thenReturn(true);

		DistributionSettleDTO dto = new DistributionSettleDTO();
		dto.setOrderId("order-1");
		dto.setBuyerUserId("buyer-1");
		dto.setOrderAmount(new BigDecimal("100.00"));
		service.settleOrder(dto);

		ArgumentCaptor<DistributionOrder> orderCaptor = ArgumentCaptor.forClass(DistributionOrder.class);
		verify(orderService).save(orderCaptor.capture());
		assertThat(orderCaptor.getValue().getStatus()).isEqualTo(DistributionOrderStatusEnum.STATUS_0.getCode());
		assertThat(orderCaptor.getValue().getSettleAt()).isNotNull();
		assertThat(distributor.getPendingCommission()).isEqualByComparingTo("10.00");
		assertThat(distributor.getAvailableCommission()).isEqualByComparingTo("0.00");
	}
}
