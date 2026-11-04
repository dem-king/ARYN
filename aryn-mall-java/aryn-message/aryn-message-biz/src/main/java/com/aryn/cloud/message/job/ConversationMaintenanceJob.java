package com.aryn.cloud.message.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.message.service.ConversationMaintenanceService;
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
import java.util.Map;

/**
 * 客服会话维护任务：关闭超时会话、回收待领取池、校正坐席负载快照。
 *
 * <p>调度中心（2026-11-04 起由 201message_job_register.sql / 204message_job_register.sql 注册）
 * 每分钟触发一次。任务按租户遍历：XXL-JOB 执行线程没有 HTTP 过滤器写入的租户上下文，
 * 必须显式 set/remove，否则会话关闭时 VO/实体写入的租户隔离字段会失真。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationMaintenanceJob {

	private final ConversationMaintenanceService maintenanceService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	@XxlJob("conversationMaintenanceJob")
	public void maintain() {
		List<SysTenant> tenants = remoteTenantService.list();
		if (CollectionUtils.isEmpty(tenants)) {
			XxlJobHelper.log("客服会话维护任务跳过：没有有效租户");
			return;
		}
		XxlJobHelper.log("客服会话维护任务开始，租户数={}", tenants.size());
		for (SysTenant tenant : tenants) {
			try {
				ArynTenantContextHolder.setTenantId(tenant.getId());
				Map<String, Object> stat = maintenanceService.maintainTenant(tenant.getId());
				XxlJobHelper.log("租户 {} 会话维护完成 {}", tenant.getId(), stat);
			}
			catch (Exception exception) {
				// 单租户失败不阻塞其它租户；下一轮调度会重试
				log.error("客服会话维护失败 tenantId={}", tenant.getId(), exception);
				XxlJobHelper.log("租户 {} 会话维护失败: {}", tenant.getId(), exception.getMessage());
			}
			finally {
				ArynTenantContextHolder.removeTenantId();
			}
		}
		XxlJobHelper.log("客服会话维护任务结束");
	}

}
