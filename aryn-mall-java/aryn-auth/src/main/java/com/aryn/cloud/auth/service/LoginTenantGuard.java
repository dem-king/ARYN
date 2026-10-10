package com.aryn.cloud.auth.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.dto.MiniAppTenantBindingDTO;
import com.aryn.cloud.user.api.remote.RemoteMiniAppTenantService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 登录租户守卫：微信登录前对 AppID↔tenantId 做权威绑定校验。
 *
 * 只依赖 user-api 的 RPC 契约，不引用 user-biz。请求租户一律取 Controller 无条件
 * 覆盖的原始请求头（requestTenantId），不取 ThreadLocal——有 token 时过滤器会改用
 * 会话租户。RPC/DB 故障按服务不可用处理（异常透传），绝不回落为校验通过。
 *
 * @author aryn
 * @since 2026-10-10
 */
@Component
@RequiredArgsConstructor
public class LoginTenantGuard {

	@DubboReference
	private RemoteMiniAppTenantService remoteMiniAppTenantService;

	/**
	 * @return 已验证的租户 ID（与权威配置严格一致）
	 */
	public String requireVerifiedTenant(String appId, String requestTenantId) {
		if (!StringUtils.hasText(appId)) {
			throw new ArynBusinessException(403, "缺少小程序 AppID，拒绝登录");
		}
		if (!StringUtils.hasText(requestTenantId)) {
			throw new ArynBusinessException(403, "缺少租户标识，拒绝登录");
		}
		MiniAppTenantBindingDTO binding = remoteMiniAppTenantService.requireBinding(appId, requestTenantId);
		if (binding == null || !Boolean.TRUE.equals(binding.getReady())
				|| !requestTenantId.equals(binding.getTenantId())) {
			throw new ArynBusinessException(403, "小程序与租户配置不一致");
		}
		return binding.getTenantId();
	}

}
