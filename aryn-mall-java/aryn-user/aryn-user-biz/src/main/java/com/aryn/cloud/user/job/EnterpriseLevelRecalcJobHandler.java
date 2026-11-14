package com.aryn.cloud.user.job;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import com.aryn.cloud.user.service.IEnterpriseMemberService;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * 企业等级月度对账任务：逐租户对全部已绑定主体重算等级。
 *
 * <p>日常等级变化由订单完成事件驱动（accumulateOrderGrowth → recalculateSubjectLevel），
 * 本任务兜底两类漂移：等级配置（门槛/档位）调整后的存量主体收敛、异常场景漏算的历史主体。
 * 建议每月 1 日凌晨执行，幂等可重跑。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnterpriseLevelRecalcJobHandler {

	private final IEnterpriseMemberService enterpriseMemberService;

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	@XxlJob("enterpriseLevelRecalcJobHandler")
	public void enterpriseLevelRecalcJobHandler() {
		XxlJobHelper.log("企业等级月度对账, enterpriseLevelRecalcJobHandler.");
		List<SysTenant> tenants = remoteTenantService.list();
		if (CollectionUtils.isEmpty(tenants)) {
			return;
		}
		int totalChanged = 0;
		for (SysTenant tenant : tenants) {
			try {
				ArynTenantContextHolder.setTenantId(tenant.getId());
				int changed = enterpriseMemberService.reconcileAllLevels(tenant.getId(), "月度对账重算");
				totalChanged += changed;
				if (changed > 0) {
					XxlJobHelper.log("租户 {} 重算主体数 {}", tenant.getId(), changed);
				}
			}
			catch (Exception ex) {
				log.error("企业等级月度对账执行失败 tenant={}", tenant.getId(), ex);
				XxlJobHelper.log("租户 {} 执行失败: {}", tenant.getId(), ex.getMessage());
			}
			finally {
				ArynTenantContextHolder.removeTenantId();
			}
		}
		XxlJobHelper.log("企业等级月度对账完成，重算主体总数 {}", totalChanged);
	}

}
