package com.aryn.cloud.promotion.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.service.IDiscountActivityService;
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

@Slf4j
@Component
@RequiredArgsConstructor
public class DiscountStatusJobHandler {

	private final IDiscountActivityService discountActivityService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	@XxlJob("discountStatusJobHandler")
	public void discountStatusJobHandler() throws Exception {
		XxlJobHelper.log("折扣活动状态流转开始, discountStatusJobHandler.");
		List<SysTenant> listSysTenant = remoteTenantService.list();
		if (!CollectionUtils.isEmpty(listSysTenant)) {
			listSysTenant.forEach(sysTenant -> {
				try {
					ArynTenantContextHolder.setTenantId(sysTenant.getId());
					discountActivityService.refreshStatus();
				} catch (Exception e) {
					log.error("租户状态刷新失败, tenantId={}", sysTenant.getId(), e);
				} finally {
					ArynTenantContextHolder.removeTenantId();
				}
			});
		}
		XxlJobHelper.log("折扣活动状态流转完成.");
	}
}