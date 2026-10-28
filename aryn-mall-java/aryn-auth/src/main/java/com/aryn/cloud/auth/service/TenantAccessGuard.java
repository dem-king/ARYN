package com.aryn.cloud.auth.service;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.dto.TenantAccessDTO;
import com.aryn.cloud.upms.api.remote.RemoteTenantService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 登录侧租户可用性守卫。
 *
 * <p>账号状态正常不代表租户仍可访问：租户停用、删除、尚未生效或授权到期后，
 * 必须拒绝签发新 token。平台租户是运维入口，始终放行。
 */
@Component
@RequiredArgsConstructor
public class TenantAccessGuard {

	@DubboReference
	private final RemoteTenantService remoteTenantService;

	public void assertAvailable(String tenantId) {
		if (!StringUtils.hasText(tenantId)) {
			throw new ArynBusinessException(403, "缺少租户标识");
		}
		if (CommonConstants.PLATFORM_TENANT_ID.equals(tenantId)) {
			return;
		}
		TenantAccessDTO access = remoteTenantService.getAccess(tenantId);
		if (access == null) {
			throw new ArynBusinessException(403, "租户不存在或已删除");
		}
		if (!CommonConstants.NORMAL_STATUS.equals(access.getStatus())) {
			throw new ArynBusinessException(403, "租户已停用，请联系平台管理员");
		}
		LocalDateTime now = LocalDateTime.now();
		if (access.getAuthBeginTime() != null && access.getAuthBeginTime().isAfter(now)) {
			throw new ArynBusinessException(403, "租户尚未生效，请联系平台管理员");
		}
		if (access.getAuthEndTime() != null && access.getAuthEndTime().isBefore(now)) {
			throw new ArynBusinessException(403, "租户授权已到期，请联系平台管理员");
		}
	}

}
