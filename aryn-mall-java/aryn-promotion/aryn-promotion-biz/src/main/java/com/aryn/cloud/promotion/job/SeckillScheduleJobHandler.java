package com.aryn.cloud.promotion.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.service.ISeckillScheduleService;
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
 * 秒杀每日排期任务：生成场次、未售库存顺延、开抢提醒。
 *
 * <p>三步都是幂等的轻量扫描，固定 60 秒执行；任务停摆恢复后，下一分钟即可补齐
 * 缺失场次与提醒，不依赖整点触发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillScheduleJobHandler {

	/** 预生成天数：今天 + 未来 2 天 */
	private static final int GENERATE_DAYS = 3;

	private final ISeckillScheduleService seckillScheduleService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	@XxlJob("seckillScheduleJobHandler")
	public void seckillScheduleJobHandler() {
		XxlJobHelper.log("秒杀每日排期任务开始.");
		List<SysTenant> tenants = remoteTenantService.list();
		if (CollectionUtils.isEmpty(tenants)) {
			XxlJobHelper.log("无租户，跳过.");
			return;
		}
		int generated = 0;
		int carried = 0;
		int reminded = 0;
		for (SysTenant tenant : tenants) {
			try {
				ArynTenantContextHolder.setTenantId(tenant.getId());
				generated += seckillScheduleService.generateSessions(GENERATE_DAYS);
				carried += seckillScheduleService.carryoverEndedSessions();
				reminded += seckillScheduleService.sendDueReminders();
			}
			catch (Exception exception) {
				log.error("秒杀每日排期任务执行失败, tenantId={}", tenant.getId(), exception);
			}
			finally {
				ArynTenantContextHolder.removeTenantId();
			}
		}
		XxlJobHelper.log("秒杀每日排期任务完成, 生成场次={}, 顺延库存={}, 提醒投递={}", generated, carried, reminded);
		log.info("秒杀每日排期任务完成, 生成场次={}, 顺延库存={}, 提醒投递={}", generated, carried, reminded);
	}
}
