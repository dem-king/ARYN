package com.aryn.cloud.order.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.service.ISpuSalesStatService;
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
 * SPU 社证统计任务：默认统计昨天（T+1），参数 days 可回填最近 N 天（1-90）。
 *
 * <p>窗口固定近 30 天（口径与热销榜/常购清单同源，见 SpuSalesStatMapper.upsertSpuStats），
 * 首次部署可用参数回填历史窗口（如传 14 预铺「近14天」展示底数）。
 * 逐租户设置上下文再聚合（与 {@link OrderDailyStatJobHandler} 同款）：
 * 租户由拦截器与 SQL 显式条件双重保证，单租户失败不阻断其他租户。
 *
 * @author aryn
 * @since 2026/11/08
 */
@Component
@RequiredArgsConstructor
public class SpuSalesStatJobHandler {

	/** 社证展示口径窗口：近 30 天（月N+人已下单 / 月回头客 / 回购率统一用此窗口） */
	private static final int STAT_WINDOW_DAYS = 30;

	/** 单次回填天数上限 */
	private static final int MAX_BACKFILL_DAYS = 90;

	private final ISpuSalesStatService spuSalesStatService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	@XxlJob("spuSalesStatJobHandler")
	public void spuSalesStatJobHandler() {
		int backfillDays = parseDays(XxlJobHelper.getJobParam());
		LocalDate end = LocalDate.now().minusDays(1);
		LocalDate start = end.minusDays(backfillDays - 1L);
		XxlJobHelper.log("SPU社证统计开始, range=" + start + "~" + end + ", window=" + STAT_WINDOW_DAYS + "天");

		List<SysTenant> tenants = remoteTenantService.list();
		if (CollectionUtils.isEmpty(tenants)) {
			XxlJobHelper.log("没有可统计的租户");
			return;
		}

		for (SysTenant tenant : tenants) {
			ArynTenantContextHolder.setTenantId(tenant.getId());
			try {
				for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
					int rows = spuSalesStatService.aggregateStats(tenant.getId(), date, STAT_WINDOW_DAYS);
					XxlJobHelper.log("tenant=" + tenant.getId() + " date=" + date + " rows=" + rows);
				}
			}
			catch (Exception ex) {
				XxlJobHelper.log("tenant=" + tenant.getId() + " 统计失败: " + ex.getMessage());
			}
			finally {
				ArynTenantContextHolder.removeTenantId();
			}
		}
	}

	private int parseDays(String param) {
		try {
			int days = Integer.parseInt(param);
			return Math.min(Math.max(days, 1), MAX_BACKFILL_DAYS);
		}
		catch (Exception ex) {
			return 1;
		}
	}

}
