package com.aryn.cloud.message.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.message.service.NoticeDispatchService;
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
 * 通知分发恢复任务：恢复心跳超时仍未完成的 PENDING/RUNNING 分发任务。
 *
 * <p>调度中心（2026-11-04 起由 201message_job_register.sql / 204message_job_register.sql 注册）
 * 每 5 分钟触发一次。按租户遍历并显式维护租户上下文，避免 XxlJob 线程缺失
 * HTTP 过滤器写入的租户信息。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NoticeDispatchRecoveryJob {

	private static final int RECOVERY_LIMIT = 100;

	private final NoticeDispatchService dispatchService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	@XxlJob("noticeDispatchRecoveryJob")
	public void recover() {
		List<SysTenant> tenants = remoteTenantService.list();
		if (CollectionUtils.isEmpty(tenants)) {
			XxlJobHelper.log("通知分发恢复任务跳过：没有有效租户");
			return;
		}
		XxlJobHelper.log("通知分发恢复任务开始，租户数={}", tenants.size());
		for (SysTenant tenant : tenants) {
			try {
				ArynTenantContextHolder.setTenantId(tenant.getId());
				int recovered = dispatchService.recoverTenant(tenant.getId(), RECOVERY_LIMIT);
				if (recovered > 0) {
					XxlJobHelper.log("租户 {} 恢复通知分发任务 {} 个", tenant.getId(), recovered);
				}
			}
			catch (Exception exception) {
				// 单租户失败不阻塞其它租户；下一轮调度会重试
				log.error("恢复通知分发任务失败 tenantId={}", tenant.getId(), exception);
				XxlJobHelper.log("租户 {} 恢复失败: {}", tenant.getId(), exception.getMessage());
			}
			finally {
				ArynTenantContextHolder.removeTenantId();
			}
		}
		XxlJobHelper.log("通知分发恢复任务结束");
	}

}
