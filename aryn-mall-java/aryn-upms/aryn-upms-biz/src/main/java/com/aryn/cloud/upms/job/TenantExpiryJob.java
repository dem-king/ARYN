package com.aryn.cloud.upms.job;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.upms.api.entity.SysTenant;
import com.aryn.cloud.upms.service.ISysTenantService;
import com.aryn.cloud.upms.support.TenantSessionRevoker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 授权到期租户的会话回收。
 *
 * <p>登录侧已拒绝到期租户签发新 token，但到期前签发的 token 仍会存活到过期。
 * 这里按小时扫描到期租户并撤销其全部在线会话；任务幂等，无会话时为空操作。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantExpiryJob {

	private final ISysTenantService sysTenantService;

	private final TenantSessionRevoker tenantSessionRevoker;

	@Scheduled(cron = "0 0 * * * ?")
	public void revokeExpiredTenantSessions() {
		LocalDateTime now = LocalDateTime.now();
		List<SysTenant> expiredTenants;
		try {
			expiredTenants = sysTenantService.list(Wrappers.<SysTenant>lambdaQuery()
				.eq(SysTenant::getStatus, CommonConstants.NORMAL_STATUS)
				.isNotNull(SysTenant::getAuthEndTime)
				.lt(SysTenant::getAuthEndTime, now));
		}
		catch (Exception e) {
			log.error("扫描授权到期租户失败", e);
			return;
		}
		for (SysTenant tenant : expiredTenants) {
			if (CommonConstants.PLATFORM_TENANT_ID.equals(tenant.getId())) {
				continue;
			}
			tenantSessionRevoker.revokeTenant(tenant.getId(), "租户授权到期");
		}
	}

}
