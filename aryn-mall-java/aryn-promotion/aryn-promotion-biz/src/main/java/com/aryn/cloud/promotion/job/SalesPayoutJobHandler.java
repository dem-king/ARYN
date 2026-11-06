package com.aryn.cloud.promotion.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.entity.SalesConfig;
import com.aryn.cloud.promotion.service.ISalesConfigService;
import com.aryn.cloud.promotion.service.ISalesCustomerClaimService;
import com.aryn.cloud.promotion.service.ISalesOrderService;
import com.aryn.cloud.promotion.service.ISalesPayoutService;
import com.aryn.cloud.promotion.service.ISalesSettlementService;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * 销售业绩定时任务（每日 01:10）
 * <p>
 * ① 认领过期置失效；② 重放未应用的退款记录；③ 到达 sales_config.monthly_cutoff_day 生成上月台账
 * （仅在配置的生成日生成，不再是每天生成）；④ 已过生成日仍存在上月未入批可发放业绩单时补跑一次，
 * 台账生成按「销售+月份」幂等，重复执行不会重复发放。
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SalesPayoutJobHandler {

	private final ISalesCustomerClaimService salesCustomerClaimService;

	private final ISalesSettlementService salesSettlementService;

	private final ISalesPayoutService salesPayoutService;

	private final ISalesOrderService salesOrderService;

	private final ISalesConfigService salesConfigService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	@XxlJob("salesPayoutJobHandler")
	public void salesPayoutJobHandler() throws Exception {
		XxlJobHelper.log("销售业绩定时任务开始：认领过期、退款重放、月度台账生成");
		List<SysTenant> tenants = remoteTenantService.list();
		if (CollectionUtils.isEmpty(tenants)) {
			return;
		}
		for (SysTenant tenant : tenants) {
			String tenantId = tenant.getId();
			try {
				ArynTenantContextHolder.setTenantId(tenantId);
				int expiredClaims = salesCustomerClaimService.expireClaims();
				int replayedRefunds = replayPendingRefunds(tenantId);
				int generatedPayouts = generateMonthlyPayoutIfDue(tenantId);
				if (expiredClaims > 0 || replayedRefunds > 0 || generatedPayouts > 0) {
					XxlJobHelper.log("租户[{}] 认领过期={} 退款重放={} 台账生成={}", tenantId, expiredClaims,
							replayedRefunds, generatedPayouts);
				}
			}
			catch (Exception e) {
				log.error("租户[{}] 销售业绩定时任务失败: {}", tenantId, e.getMessage(), e);
				XxlJobHelper.log("Tenant [{}] sales payout task failed: {}", tenantId, e.getMessage());
			}
			finally {
				ArynTenantContextHolder.removeTenantId();
			}
		}
	}

	private int replayPendingRefunds(String tenantId) {
		int replayed = 0;
		for (String refundRecordId : salesSettlementService.listPendingRefundIds()) {
			try {
				if (Boolean.TRUE.equals(salesSettlementService.replayPendingRefund(refundRecordId))) {
					replayed++;
				}
			}
			catch (Exception e) {
				log.warn("租户[{}] 销售退款记录[{}]重放失败: {}", tenantId, refundRecordId, e.getMessage());
				XxlJobHelper.log("Tenant [{}] sales refund record [{}] replay failed: {}", tenantId,
						refundRecordId, e.getMessage());
			}
		}
		return replayed;
	}

	private int generateMonthlyPayoutIfDue(String tenantId) {
		SalesConfig config = salesConfigService.getActiveConfigOrDefaults();
		int cutoffDay = config.getMonthlyCutoffDay() == null ? 1 : config.getMonthlyCutoffDay();
		LocalDate today = LocalDate.now();
		String lastMonth = YearMonth.now().minusMonths(1).toString();
		// 只在配置的台账生成日生成上月台账（cutoffDay 默认 1，不在 1 号以外的日子每天生成）
		boolean generationDay = today.getDayOfMonth() == cutoffDay;
		// 已过生成日但上月仍有未入批的可发放业绩单（生成日任务失败/漏跑）时补跑一次，生成幂等
		boolean catchUp = !generationDay && today.getDayOfMonth() > cutoffDay
				&& salesOrderService.existsUnbatchedPayoutOrders(thisMonthStart());
		if (!generationDay && !catchUp) {
			return 0;
		}
		int generated = salesPayoutService.generate(lastMonth);
		if (generated > 0) {
			log.info("租户[{}] 生成销售提成台账 payoutMonth={}, generated={}", tenantId, lastMonth, generated);
		}
		return generated;
	}

	/**
	 * 台账月份上限：本月 1 日 0 点（上月台账只纳入此前支付的业绩单）。
	 */
	private LocalDateTime thisMonthStart() {
		return YearMonth.now().atDay(1).atStartOfDay();
	}

}
