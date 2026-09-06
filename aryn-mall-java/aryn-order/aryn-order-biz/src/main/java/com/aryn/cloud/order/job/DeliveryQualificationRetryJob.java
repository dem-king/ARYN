
package com.aryn.cloud.order.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.service.IDeliveryQualificationOperationService;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 配送资格操作补偿任务
 *
 * <p>UPMS 角色授予/回收是跨服务远程操作，无法随订单域本地事务回滚。
 * 资格操作以 outbox 记录，事务提交后同步执行一次；本任务对仍待处理的
 * 记录按指数退避重试，直到最终成功，保证不残留孤儿角色或未回收权限。
 *
 * <p>使用 Spring 调度而非 XXL-JOB，避免依赖调度中心手工注册；
 * 多实例并发由操作记录的条件认领保证幂等。
 *
 * @author aryn
 * @since 2026/9/6
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryQualificationRetryJob {

	private final IDeliveryQualificationOperationService qualificationOperationService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	/**
	 * 每 60 秒扫描一次各租户到期的待处理资格操作
	 */
	@Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
	public void retryPendingOperations() {
		List<SysTenant> tenants = remoteTenantService.list();
		if (CollectionUtils.isEmpty(tenants)) {
			return;
		}
		tenants.forEach(tenant -> {
			try {
				ArynTenantContextHolder.setTenantId(tenant.getId());
				qualificationOperationService.listDueForRetry(20)
					.forEach(operation -> qualificationOperationService.processPending(operation.getId()));
			}
			catch (Exception e) {
				log.error("配送资格操作重试任务执行异常：tenantId={}", tenant.getId(), e);
			}
			finally {
				ArynTenantContextHolder.removeTenantId();
			}
		});
	}

}
