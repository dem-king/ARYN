package com.aryn.cloud.order.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.service.ICodPayRemindService;
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
 * 货到付款收款预警定时任务：收货后超时未收款的 COD 订单，向买家与租户管理员发站内信。
 *
 * @author Aetheryn
 * @date 2026/10/03
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodPayRemindJobHandler {

	private final ICodPayRemindService codPayRemindService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	@XxlJob("codPayRemindJobHandler")
	public void codPayRemindJobHandler() {
		XxlJobHelper.log("扫描收货后超时未收款的货到付款订单, codPayRemindJobHandler.");
		List<SysTenant> tenants = remoteTenantService.list();
		if (CollectionUtils.isEmpty(tenants)) {
			return;
		}
		tenants.forEach(sysTenant -> {
			try {
				ArynTenantContextHolder.setTenantId(sysTenant.getId());
				int sent = codPayRemindService.remindTenant(sysTenant.getId());
				if (sent > 0) {
					XxlJobHelper.log("租户 {} 发送货到付款收款提醒 {} 条", sysTenant.getId(), sent);
				}
			}
			catch (Exception e) {
				log.error("货到付款收款提醒任务执行失败 tenant={}", sysTenant.getId(), e);
				XxlJobHelper.log("租户 {} 执行失败: {}", sysTenant.getId(), e.getMessage());
			}
			finally {
				ArynTenantContextHolder.removeTenantId();
			}
		});
	}

}
