
package com.aryn.cloud.order.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.service.ISharedCartService;
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
 * 共享购物车过期关闭任务。
 *
 * <p>收集有效期 24 小时，超时后状态必须由「收集中」变为「已关闭」，
 * 否则列表会长期显示误导性的进行中状态，且新采购会因
 * uk_shared_cart_active 唯一约束而被引导到早已无人问津的旧购物车。
 *
 * <p>使用 Spring 调度而非 XXL-JOB，避免依赖调度中心手工注册；
 * 多实例并发由条件更新（WHERE status IN ('2','3')）保证幂等。
 *
 * @author aryn
 * @since 2026/9/20
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SharedCartExpireJob {

	/** 单租户单轮处理上限，避免长事务 */
	private static final int BATCH_SIZE = 200;

	private final ISharedCartService sharedCartService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	/**
	 * 每 5 分钟扫描一次各租户的过期共享购物车并关闭
	 */
	@Scheduled(fixedDelay = 300_000L, initialDelay = 300_000L)
	public void closeExpiredCarts() {
		List<SysTenant> tenants = remoteTenantService.list();
		if (CollectionUtils.isEmpty(tenants)) {
			return;
		}
		tenants.forEach(tenant -> {
			try {
				ArynTenantContextHolder.setTenantId(tenant.getId());
				int closed = sharedCartService.closeExpiredCarts(BATCH_SIZE);
				if (closed > 0) {
					log.info("租户[{}]共享购物车过期关闭 {} 条", tenant.getId(), closed);
				}
			}
			catch (Exception e) {
				log.error("共享购物车过期关闭任务执行异常：tenantId={}", tenant.getId(), e);
			}
			finally {
				ArynTenantContextHolder.removeTenantId();
			}
		});
	}

}
