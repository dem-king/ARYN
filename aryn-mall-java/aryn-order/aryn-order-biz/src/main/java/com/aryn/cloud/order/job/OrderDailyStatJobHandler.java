package com.aryn.cloud.order.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.service.IDailyUserOrderStatService;
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
 * 用户日订单汇总任务：默认汇总昨天，参数 days 可回填最近 N 天（1-90）。
 */
@Component
@RequiredArgsConstructor
public class OrderDailyStatJobHandler {

	private final IDailyUserOrderStatService dailyUserOrderStatService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	@XxlJob("orderDailyStatJobHandler")
	public void orderDailyStatJobHandler() {
		int days = parseDays(XxlJobHelper.getJobParam());
		LocalDate end = LocalDate.now().minusDays(1);
		LocalDate start = end.minusDays(days - 1L);
		XxlJobHelper.log("用户日订单汇总开始, range=" + start + "~" + end);

		List<SysTenant> tenants = remoteTenantService.list();
		if (CollectionUtils.isEmpty(tenants)) {
			XxlJobHelper.log("没有可汇总的租户");
			return;
		}

		for (SysTenant tenant : tenants) {
			ArynTenantContextHolder.setTenantId(tenant.getId());
			try {
				for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
					int rows = dailyUserOrderStatService.aggregateDay(tenant.getId(), date);
					XxlJobHelper.log("tenant=" + tenant.getId() + " date=" + date + " rows=" + rows);
				}
			}
			catch (Exception ex) {
				XxlJobHelper.log("tenant=" + tenant.getId() + " 汇总失败: " + ex.getMessage());
			}
			finally {
				ArynTenantContextHolder.removeTenantId();
			}
		}
	}

	private int parseDays(String param) {
		try {
			int days = Integer.parseInt(param);
			return Math.min(Math.max(days, 1), 90);
		}
		catch (Exception ex) {
			return 1;
		}
	}

}
