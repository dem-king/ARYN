package com.aryn.cloud.user.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import com.aryn.cloud.user.service.IPointsExpireService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 积分过期清零定时任务：按租户扫描到期批次，清零剩余量并扣减可用积分。
 *
 * <p>行业口径：小象/山姆 365 天滚动到期、京东 2026 年起收紧到 180 天——有效期既是
 * 财务负债控制（递延负债不再无限累积），也是「再不来就过期」的复购杠杆。
 *
 * @author Aetheryn
 * @date 2026/10/24
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointsExpireJobHandler {

	private final IPointsExpireService pointsExpireService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	@XxlJob("pointsExpireJobHandler")
	public void pointsExpireJobHandler() {
		XxlJobHelper.log("扫描到期积分批次并清零, pointsExpireJobHandler.");
		List<SysTenant> tenants = remoteTenantService.list();
		if (CollectionUtils.isEmpty(tenants)) {
			return;
		}
		tenants.forEach(sysTenant -> {
			try {
				ArynTenantContextHolder.setTenantId(sysTenant.getId());
				int expired = pointsExpireService.expireTenant();
				if (expired > 0) {
					XxlJobHelper.log("租户 {} 清零过期积分用户数 {}", sysTenant.getId(), expired);
				}
			}
			catch (Exception e) {
				log.error("积分过期清零任务执行失败 tenant={}", sysTenant.getId(), e);
				XxlJobHelper.log("租户 {} 执行失败: {}", sysTenant.getId(), e.getMessage());
			}
			finally {
				ArynTenantContextHolder.removeTenantId();
			}
		});
	}

}
