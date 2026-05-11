package com.aryn.cloud.promotion.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.entity.DistributionOrder;
import com.aryn.cloud.promotion.api.enums.DistributionOrderStatusEnum;
import com.aryn.cloud.promotion.service.IDistributionOrderService;
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

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	/**
	 * 扫描待结算的分销订单，超过结算周期后自动标记为已结算
	 */
	@XxlJob("distributionSettleJobHandler")
	public void distributionSettleJobHandler() throws Exception {
		XxlJobHelper.log("扫描待结算的分销订单, distributionSettleJobHandler.");
		List<SysTenant> listSysTenant = remoteTenantService.list();
		if (!CollectionUtils.isEmpty(listSysTenant)) {
			listSysTenant.forEach(sysTenant -> {
				ArynTenantContextHolder.setTenantId(sysTenant.getId());
				// 查询所有待结算状态的分销订单
				List<DistributionOrder> pendingOrders = distributionOrderService.list(
					Wrappers.<DistributionOrder>lambdaQuery()
						.eq(DistributionOrder::getStatus, DistributionOrderStatusEnum.STATUS_0.getCode())
						.le(DistributionOrder::getCreateTime, LocalDateTime.now().minusDays(7))
				);
				pendingOrders.forEach(order -> {
					order.setStatus(DistributionOrderStatusEnum.STATUS_1.getCode());
					order.setSettleTime(LocalDateTime.now());
					order.setUpdateBy("job");
					distributionOrderService.updateById(order);
				});
				if (!pendingOrders.isEmpty()) {
					XxlJobHelper.log("租户[{}]处理{}条待结算分销订单", sysTenant.getId(), pendingOrders.size());
				}
				ArynTenantContextHolder.removeTenantId();
			});
		}
	}
}
