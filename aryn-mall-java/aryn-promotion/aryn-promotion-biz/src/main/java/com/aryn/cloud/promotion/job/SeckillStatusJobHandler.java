package com.aryn.cloud.promotion.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.service.ISeckillActivityService;
import com.aryn.cloud.promotion.service.ISeckillSessionService;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.promotion.api.entity.SeckillActivity;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillStatusJobHandler {

	private final ISeckillActivityService seckillActivityService;

	private final ISeckillSessionService seckillSessionService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	@XxlJob("seckillStatusJobHandler")
	public void seckillStatusJobHandler() throws Exception {
		XxlJobHelper.log("秒杀活动状态流转开始, seckillStatusJobHandler.");
		List<SysTenant> listSysTenant = remoteTenantService.list();
		if (!CollectionUtils.isEmpty(listSysTenant)) {
			listSysTenant.forEach(sysTenant -> {
				try {
					ArynTenantContextHolder.setTenantId(sysTenant.getId());
					// 刷新场次状态
					seckillSessionService.refreshStatus();
					// 刷新活动状态
					refreshActivityStatus();
				} catch (Exception e) {
					log.error("租户状态刷新失败, tenantId={}", sysTenant.getId(), e);
				} finally {
					ArynTenantContextHolder.removeTenantId();
				}
			});
		}
		XxlJobHelper.log("秒杀活动状态流转完成.");
	}

	/**
	 * 刷新活动状态：根据时间自动流转
	 */
	private void refreshActivityStatus() {
		LocalDateTime now = LocalDateTime.now();
		// 未开始 → 进行中
		seckillActivityService.update(Wrappers.<SeckillActivity>lambdaUpdate()
				.eq(SeckillActivity::getStatus, 0)
				.le(SeckillActivity::getStartTime, now)
				.gt(SeckillActivity::getEndTime, now)
				.set(SeckillActivity::getStatus, 1));
		// 进行中 → 已结束
		seckillActivityService.update(Wrappers.<SeckillActivity>lambdaUpdate()
				.eq(SeckillActivity::getStatus, 1)
				.le(SeckillActivity::getEndTime, now)
				.set(SeckillActivity::getStatus, 2));
	}
}