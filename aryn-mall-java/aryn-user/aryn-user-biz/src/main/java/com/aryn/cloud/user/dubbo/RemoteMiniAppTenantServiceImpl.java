package com.aryn.cloud.user.dubbo;

import com.aryn.cloud.user.api.dto.MiniAppTenantBindingDTO;
import com.aryn.cloud.user.api.entity.SocialAccount;
import com.aryn.cloud.user.api.remote.RemoteMiniAppTenantService;
import com.aryn.cloud.user.service.MiniAppBindingResolver;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

/**
 * 小程序租户绑定权威校验 RPC。
 *
 * 返回最小绑定 DTO（无 secret/手机号/凭证）；ready 仅代表
 * "权威映射与本实例 SDK 输入齐备"，不代表微信 secret 已远程验证或支付已开通。
 *
 * @author aryn
 * @since 2026-10-10
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteMiniAppTenantServiceImpl implements RemoteMiniAppTenantService {

	private final MiniAppBindingResolver miniAppBindingResolver;

	@Override
	public MiniAppTenantBindingDTO requireBinding(String appId, String expectedTenantId) {
		SocialAccount account = miniAppBindingResolver.requireBinding(appId, expectedTenantId);
		MiniAppTenantBindingDTO dto = new MiniAppTenantBindingDTO();
		dto.setAppId(account.getAppId());
		dto.setTenantId(account.getTenantId());
		dto.setAccountId(account.getId());
		dto.setConfigFingerprint(String.valueOf(account.getUpdateTime()));
		dto.setReady(Boolean.TRUE);
		return dto;
	}

}
