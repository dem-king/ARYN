package com.aryn.cloud.promotion.job;

import com.aryn.cloud.promotion.api.entity.SalesConfig;
import com.aryn.cloud.promotion.service.ISalesConfigService;
import com.aryn.cloud.promotion.service.ISalesCustomerClaimService;
import com.aryn.cloud.promotion.service.ISalesOrderService;
import com.aryn.cloud.promotion.service.ISalesPayoutService;
import com.aryn.cloud.promotion.service.ISalesSettlementService;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 销售业绩定时任务单测：租户隔离、认领过期、退款重放、仅生成日生成台账、漏跑补跑
 */
class SalesPayoutJobHandlerTest {

	private ISalesCustomerClaimService claimService;

	private ISalesSettlementService settlementService;

	private ISalesPayoutService payoutService;

	private ISalesOrderService salesOrderService;

	private ISalesConfigService configService;

	private RemoteTenantService tenantService;

	private SalesPayoutJobHandler handler;

	@BeforeEach
	void setUp() {
		claimService = mock(ISalesCustomerClaimService.class);
		settlementService = mock(ISalesSettlementService.class);
		payoutService = mock(ISalesPayoutService.class);
		salesOrderService = mock(ISalesOrderService.class);
		configService = mock(ISalesConfigService.class);
		tenantService = mock(RemoteTenantService.class);
		when(tenantService.list()).thenReturn(List.of(tenant("tenant-1")));
		when(claimService.expireClaims()).thenReturn(0);
		when(settlementService.listPendingRefundIds()).thenReturn(List.of());
		handler = new SalesPayoutJobHandler(claimService, settlementService, payoutService, salesOrderService,
				configService, tenantService);
	}

	@Test
	void failedTenantDoesNotBlockFollowingTenant() throws Exception {
		when(tenantService.list()).thenReturn(List.of(tenant("tenant-1"), tenant("tenant-2")));
		when(claimService.expireClaims()).thenThrow(new IllegalStateException("poison tenant")).thenReturn(0);
		when(configService.getActiveConfigOrDefaults())
			.thenReturn(new SalesConfig().setMonthlyCutoffDay(LocalDate.now().getDayOfMonth()));
		when(payoutService.generate(any())).thenReturn(0);

		handler.salesPayoutJobHandler();

		verify(claimService, times(2)).expireClaims();
	}

	@Test
	void generatesLastMonthPayoutOnCutoffDay() throws Exception {
		when(configService.getActiveConfigOrDefaults())
			.thenReturn(new SalesConfig().setMonthlyCutoffDay(LocalDate.now().getDayOfMonth()));
		when(payoutService.generate(any())).thenReturn(1);

		handler.salesPayoutJobHandler();

		verify(payoutService).generate(YearMonth.now().minusMonths(1).toString());
		// 生成日直接生成，不需要再做补跑判断
		verify(salesOrderService, never()).existsUnbatchedPayoutOrders(any());
	}

	@Test
	void skipsPayoutGenerationWhenNotCutoffDayAndNoUnbatchedOrders() throws Exception {
		// 构造一个今天不是生成日的配置：非 1 号时取 1 号（已过生成日但无待补跑单），1 号时取 2 号（未到生成日）
		int cutoffDay = LocalDate.now().getDayOfMonth() == 1 ? 2 : 1;
		when(configService.getActiveConfigOrDefaults())
			.thenReturn(new SalesConfig().setMonthlyCutoffDay(cutoffDay));
		when(salesOrderService.existsUnbatchedPayoutOrders(any())).thenReturn(false);

		handler.salesPayoutJobHandler();

		verify(payoutService, never()).generate(any());
	}

	@Test
	void catchesUpPayoutGenerationWhenUnbatchedOrdersExist() throws Exception {
		Assumptions.assumeTrue(LocalDate.now().getDayOfMonth() > 1,
				"当月 1 号本身就是默认生成日，无法构造非生成日补跑场景");
		when(configService.getActiveConfigOrDefaults()).thenReturn(new SalesConfig().setMonthlyCutoffDay(1));
		when(salesOrderService.existsUnbatchedPayoutOrders(YearMonth.now().atDay(1).atStartOfDay()))
			.thenReturn(true);
		when(payoutService.generate(any())).thenReturn(1);

		handler.salesPayoutJobHandler();

		verify(salesOrderService).existsUnbatchedPayoutOrders(YearMonth.now().atDay(1).atStartOfDay());
		verify(payoutService).generate(YearMonth.now().minusMonths(1).toString());
	}

	@Test
	void replaysPendingRefundRecordsPerTenant() throws Exception {
		when(claimService.expireClaims()).thenReturn(2);
		when(settlementService.listPendingRefundIds()).thenReturn(List.of("refund-1", "refund-2"));
		when(settlementService.replayPendingRefund("refund-1"))
			.thenThrow(new IllegalStateException("poison"))
			.thenReturn(true);
		when(configService.getActiveConfigOrDefaults())
			.thenReturn(new SalesConfig().setMonthlyCutoffDay(LocalDate.now().getDayOfMonth()));

		handler.salesPayoutJobHandler();

		verify(settlementService).replayPendingRefund("refund-2");
	}

	private SysTenant tenant(String id) {
		SysTenant tenant = new SysTenant();
		tenant.setId(id);
		return tenant;
	}

}
