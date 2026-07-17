package com.aryn.cloud.promotion.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.entity.DistributionOrder;
import com.aryn.cloud.promotion.api.enums.DistributionOrderStatusEnum;
import com.aryn.cloud.promotion.service.IDistributionOrderService;
import com.aryn.cloud.promotion.service.IDistributionSettlementService;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分销结算定时任务
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
@Component
@RequiredArgsConstructor
public class DistributionSettleJobHandler {

	private final IDistributionOrderService distributionOrderService;

	private final IDistributionSettlementService distributionSettlementService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	/**
	 * 扫描到期的待结算分销订单并释放佣金
	 */
	@XxlJob("distributionSettleJobHandler")
	public void distributionSettleJobHandler() throws Exception {
		XxlJobHelper.log("扫描待结算的分销订单, distributionSettleJobHandler.");
		List<SysTenant> listSysTenant = remoteTenantService.list();
		if (!CollectionUtils.isEmpty(listSysTenant)) {
			for (SysTenant sysTenant : listSysTenant) {
				try {
					ArynTenantContextHolder.setTenantId(sysTenant.getId());
					int replayedRefunds = replayPendingRefunds(sysTenant.getId());
					List<DistributionOrder> pendingOrders = distributionOrderService.list(
						Wrappers.<DistributionOrder>lambdaQuery()
							.eq(DistributionOrder::getStatus, DistributionOrderStatusEnum.STATUS_0.getCode())
							.le(DistributionOrder::getSettleAt, LocalDateTime.now())
					);
					for (DistributionOrder order : pendingOrders) {
						try {
							distributionSettlementService.settlePendingOrder(order.getId());
						}
						catch (Exception e) {
							XxlJobHelper.log("Tenant [{}] distribution order [{}] settlement failed: {}",
								sysTenant.getId(), order.getId(), e.getMessage());
						}
					}
					if (!pendingOrders.isEmpty()) {
						XxlJobHelper.log("租户[{}]处理{}条待结算分销订单", sysTenant.getId(), pendingOrders.size());
					}
					if (replayedRefunds > 0) {
						XxlJobHelper.log("租户[{}]重放{}条待应用分销退款", sysTenant.getId(), replayedRefunds);
					}
				}
				catch (Exception e) {
					XxlJobHelper.log("Tenant [{}] distribution settlement task failed: {}",
						sysTenant.getId(), e.getMessage());
				}
				finally {
					ArynTenantContextHolder.removeTenantId();
				}
			}
		}
	}

	private int replayPendingRefunds(String tenantId) {
		int replayed = 0;
		for (String refundRecordId : distributionSettlementService.listPendingRefundIds()) {
			try {
				if (Boolean.TRUE.equals(distributionSettlementService.replayPendingRefund(refundRecordId))) {
					replayed++;
				}
			}
			catch (Exception e) {
				XxlJobHelper.log("Tenant [{}] distribution refund record [{}] replay failed: {}",
					tenantId, refundRecordId, e.getMessage());
			}
		}
		return replayed;
	}
}
