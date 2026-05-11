package com.aryn.cloud.promotion.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.service.IGroupBuyRecordService;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GroupBuyExpireJobHandler {

	private final IGroupBuyRecordService groupBuyRecordService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	@XxlJob("groupBuyExpireJobHandler")
	public void groupBuyExpireJobHandler() throws Exception {
		XxlJobHelper.log("拼团超时处理开始, groupBuyExpireJobHandler.");
		List<SysTenant> listSysTenant = remoteTenantService.list();
		if (!CollectionUtils.isEmpty(listSysTenant)) {
			listSysTenant.forEach(sysTenant -> {
				ArynTenantContextHolder.setTenantId(sysTenant.getId());
				groupBuyRecordService.handleGroupExpire();
				groupBuyRecordService.handleActivityExpire();
				ArynTenantContextHolder.removeTenantId();
			});
		}
		XxlJobHelper.log("拼团超时处理完成.");
	}
}
