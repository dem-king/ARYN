package com.aryn.cloud.vessel.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import com.aryn.cloud.vessel.service.VesselService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 靠港状态流转定时任务：按 ETA/ETD 把靠港计划从「计划中」推进到「靠泊中」「已完成」。
 *
 * <p>vessel_call.status 此前只在新增时写入 1（计划中），无任何流转逻辑，
 * 过期靠港恒显示「计划中」。本任务与秒杀状态任务（seckillStatusJobHandler）
 * 同套路：逐租户按时间批量推进，重复执行幂等。
 *
 * @author Aetheryn
 * @date 2026/10/03
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VesselCallStatusJobHandler {

	private final VesselService vesselService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	@XxlJob("vesselCallStatusJobHandler")
	public void vesselCallStatusJobHandler() {
		XxlJobHelper.log("靠港状态流转开始, vesselCallStatusJobHandler.");
		List<SysTenant> tenants = remoteTenantService.list();
		if (CollectionUtils.isEmpty(tenants)) {
			return;
		}
		tenants.forEach(sysTenant -> {
			try {
				ArynTenantContextHolder.setTenantId(sysTenant.getId());
				int changed = vesselService.refreshCallStatus(sysTenant.getId());
				if (changed > 0) {
					XxlJobHelper.log("租户 {} 推进靠港状态 {} 条", sysTenant.getId(), changed);
				}
			}
			catch (Exception e) {
				log.error("靠港状态流转任务执行失败 tenant={}", sysTenant.getId(), e);
				XxlJobHelper.log("租户 {} 执行失败: {}", sysTenant.getId(), e.getMessage());
			}
			finally {
				ArynTenantContextHolder.removeTenantId();
			}
		});
		XxlJobHelper.log("靠港状态流转完成.");
	}

}
