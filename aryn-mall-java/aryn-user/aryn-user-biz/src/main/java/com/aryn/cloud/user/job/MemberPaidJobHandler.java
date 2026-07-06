package com.aryn.cloud.user.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import com.aryn.cloud.user.service.IMemberPaidOrderService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 付费会员定时任务
 *
 * @author aryn
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemberPaidJobHandler {

	private final IMemberPaidOrderService memberPaidOrderService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	/**
	 * 检查付费会员到期
	 */
	@XxlJob("checkMemberExpiredJobHandler")
	public void checkMemberExpiredJobHandler() {
		XxlJobHelper.log("检查付费会员到期, checkMemberExpiredJobHandler.");
		try {
			List<SysTenant> listSysTenant = remoteTenantService.list();
			if (!CollectionUtils.isEmpty(listSysTenant)) {
				listSysTenant.forEach(sysTenant -> {
					ArynTenantContextHolder.setTenantId(sysTenant.getId());
					memberPaidOrderService.checkExpiredOrders();
					ArynTenantContextHolder.removeTenantId();
				});
			}
		}
		catch (Exception e) {
			log.error("检查付费会员到期异常", e);
			XxlJobHelper.handleFail("检查付费会员到期异常: " + e.getMessage());
			return;
		}
		XxlJobHelper.handleSuccess("检查付费会员到期完成");
	}

}