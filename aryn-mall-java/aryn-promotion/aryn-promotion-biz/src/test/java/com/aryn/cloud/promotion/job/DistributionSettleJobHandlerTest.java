package com.aryn.cloud.promotion.job;

import com.aryn.cloud.promotion.api.entity.DistributionOrder;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.aryn.cloud.promotion.service.IDistributionOrderService;
import com.aryn.cloud.promotion.service.IDistributionSettlementService;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DistributionSettleJobHandlerTest {

	@Test
	void failedTenantDoesNotBlockFollowingTenant() throws Exception {
		IDistributionOrderService orderService = mock(IDistributionOrderService.class);
		IDistributionSettlementService settlementService = mock(IDistributionSettlementService.class);
		RemoteTenantService tenantService = mock(RemoteTenantService.class);
		when(tenantService.list()).thenReturn(List.of(tenant("tenant-1"), tenant("tenant-2")));
		when(settlementService.listPendingRefundIds())
			.thenThrow(new IllegalStateException("poison refund"))
			.thenReturn(List.of());
		when(orderService.list(any(Wrapper.class))).thenReturn(List.of());
		DistributionSettleJobHandler handler = new DistributionSettleJobHandler(
			orderService, settlementService, tenantService);

		handler.distributionSettleJobHandler();

		verify(settlementService, times(2)).listPendingRefundIds();
	}

	@Test
	void failedOrderDoesNotBlockFollowingOrder() throws Exception {
		IDistributionOrderService orderService = mock(IDistributionOrderService.class);
		IDistributionSettlementService settlementService = mock(IDistributionSettlementService.class);
		RemoteTenantService tenantService = mock(RemoteTenantService.class);
		DistributionOrder first = new DistributionOrder().setId("order-1");
		DistributionOrder second = new DistributionOrder().setId("order-2");
		when(tenantService.list()).thenReturn(List.of(tenant("tenant-1")));
		when(settlementService.listPendingRefundIds()).thenReturn(List.of());
		when(orderService.list(any(Wrapper.class))).thenReturn(List.of(first, second));
		when(settlementService.settlePendingOrder("order-1"))
			.thenThrow(new IllegalStateException("poison order"));
		DistributionSettleJobHandler handler = new DistributionSettleJobHandler(
			orderService, settlementService, tenantService);

		handler.distributionSettleJobHandler();

		verify(settlementService).settlePendingOrder("order-2");
	}

	@Test
	void failedRefundDoesNotBlockFollowingRefund() throws Exception {
		IDistributionOrderService orderService = mock(IDistributionOrderService.class);
		IDistributionSettlementService settlementService = mock(IDistributionSettlementService.class);
		RemoteTenantService tenantService = mock(RemoteTenantService.class);
		when(tenantService.list()).thenReturn(List.of(tenant("tenant-1")));
		when(settlementService.listPendingRefundIds()).thenReturn(List.of("refund-1", "refund-2"));
		when(settlementService.replayPendingRefund("refund-1"))
			.thenThrow(new IllegalStateException("poison refund"));
		when(settlementService.replayPendingRefund("refund-2")).thenReturn(true);
		when(orderService.list(any(Wrapper.class))).thenReturn(List.of());
		DistributionSettleJobHandler handler = new DistributionSettleJobHandler(
			orderService, settlementService, tenantService);

		handler.distributionSettleJobHandler();

		verify(settlementService).replayPendingRefund("refund-2");
	}

	private SysTenant tenant(String id) {
		SysTenant tenant = new SysTenant();
		tenant.setId(id);
		return tenant;
	}
}
