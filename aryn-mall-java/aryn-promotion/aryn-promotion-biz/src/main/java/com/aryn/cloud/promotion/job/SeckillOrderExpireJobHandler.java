package com.aryn.cloud.promotion.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.entity.SeckillOrder;
import com.aryn.cloud.promotion.service.ISeckillOrderService;
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
 * 秒杀预扣超时释放定时任务
 * 扫描超时未支付的秒杀订单，回滚 Redis 库存 + 标记已超时
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillOrderExpireJobHandler {

	private final ISeckillOrderService seckillOrderService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	private static final int EXPIRE_MINUTES = 15;

	private static final int BATCH_SIZE = 200;

	@XxlJob("seckillOrderExpireJobHandler")
	public void seckillOrderExpireJobHandler() throws Exception {
		XxlJobHelper.log("秒杀预扣超时释放开始, seckillOrderExpireJobHandler.");
		List<SysTenant> listSysTenant = remoteTenantService.list();
		if (!CollectionUtils.isEmpty(listSysTenant)) {
			listSysTenant.forEach(sysTenant -> {
				try {
					ArynTenantContextHolder.setTenantId(sysTenant.getId());
					processExpiredOrders();
				} catch (Exception e) {
					log.error("租户秒杀超时释放失败, tenantId={}", sysTenant.getId(), e);
				} finally {
					ArynTenantContextHolder.removeTenantId();
				}
			});
		}
		XxlJobHelper.log("秒杀预扣超时释放完成.");
	}

	private void processExpiredOrders() {
		List<SeckillOrder> expiredOrders = seckillOrderService.listExpiredUnpaid(EXPIRE_MINUTES);
		if (CollectionUtils.isEmpty(expiredOrders)) {
			return;
		}
		log.info("发现{}条超时未支付秒杀订单，开始处理", expiredOrders.size());
		int processed = 0;
		for (SeckillOrder order : expiredOrders) {
			try {
				boolean success = seckillOrderService.expireOrder(order.getOrderId());
				if (success) {
					processed++;
				}
			} catch (Exception e) {
				log.error("超时订单处理失败, orderId={}", order.getOrderId(), e);
			}
		}
		log.info("超时秒杀订单处理完成, 总数={}, 成功={}", expiredOrders.size(), processed);
	}
}