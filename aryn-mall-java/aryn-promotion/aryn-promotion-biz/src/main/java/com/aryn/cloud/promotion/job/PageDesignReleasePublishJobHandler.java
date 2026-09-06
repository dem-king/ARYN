package com.aryn.cloud.promotion.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.service.PageDesignReleaseService;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 页面装修定时发布调度任务。
 * <p>
 * 逐租户扫描到达计划时间的待发布申请并执行发布；单租户失败不影响其他租户。
 *
 * @author 雨滴kian
 * @date 2026/09/06
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PageDesignReleasePublishJobHandler {

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	private final PageDesignReleaseService pageDesignReleaseService;

	@XxlJob("pageDesignReleasePublishJobHandler")
	public void pageDesignReleasePublishJobHandler() {
		XxlJobHelper.log("扫描到期待发布装修申请, pageDesignReleasePublishJobHandler.");
		List<SysTenant> tenants = remoteTenantService.list();
		int total = 0;
		if (!CollectionUtils.isEmpty(tenants)) {
			for (SysTenant tenant : tenants) {
				try {
					ArynTenantContextHolder.setTenantId(tenant.getId());
					total += pageDesignReleaseService.publishDueReleases();
				}
				catch (RuntimeException exception) {
					log.warn("租户装修定时发布扫描失败 tenantId={} reason={}", tenant.getId(), exception.getMessage());
				}
				finally {
					ArynTenantContextHolder.removeTenantId();
				}
			}
		}
		XxlJobHelper.log("本次定时发布完成, 共发布 " + total + " 条申请");
		XxlJobHelper.handleSuccess("published=" + total);
	}

}
