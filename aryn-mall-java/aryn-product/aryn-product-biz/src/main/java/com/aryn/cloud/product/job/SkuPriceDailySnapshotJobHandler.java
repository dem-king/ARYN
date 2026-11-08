package com.aryn.cloud.product.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.product.service.IGoodsSkuPriceDailyService;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * SKU 价格快照任务：每日 01:30 对全部租户的在售 SKU 写当日快照行。
 *
 * <p>价格是时点数据无法回填历史——不支持参数回填，重跑即用当前价覆盖当日行
 * （快照表空窗的窗口从部署日起逐日积累，「近N天最低价」自然灰度放量）。
 * 逐租户设置上下文再快照（与 {@code SpuSalesStatJobHandler} 同款惯例）：
 * 租户由拦截器与 SQL 显式条件双重保证，单租户失败不阻断其他租户。
 *
 * @author aryn
 * @since 2026/11/08
 */
@Component
@RequiredArgsConstructor
public class SkuPriceDailySnapshotJobHandler {

	private final IGoodsSkuPriceDailyService goodsSkuPriceDailyService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	@XxlJob("skuPriceDailySnapshotJobHandler")
	public void skuPriceDailySnapshotJobHandler() {
		LocalDate statDate = LocalDate.now();
		XxlJobHelper.log("SKU价格快照开始, statDate=" + statDate);

		List<SysTenant> tenants = remoteTenantService.list();
		if (CollectionUtils.isEmpty(tenants)) {
			XxlJobHelper.log("没有可快照的租户");
			return;
		}

		for (SysTenant tenant : tenants) {
			ArynTenantContextHolder.setTenantId(tenant.getId());
			try {
				int rows = goodsSkuPriceDailyService.snapshotDaily(tenant.getId(), statDate);
				XxlJobHelper.log("tenant=" + tenant.getId() + " rows=" + rows);
			}
			catch (Exception ex) {
				XxlJobHelper.log("tenant=" + tenant.getId() + " 快照失败: " + ex.getMessage());
			}
			finally {
				ArynTenantContextHolder.removeTenantId();
			}
		}
	}

}
