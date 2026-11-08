package com.aryn.cloud.user.dubbo;

import com.aryn.cloud.common.core.enums.MiniAppIdentityModeEnum;
import com.aryn.cloud.common.core.enums.OpenPlatformTypeEnum;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.dto.MiniAppBuyerIdentityVerifyDTO;
import com.aryn.cloud.user.api.dto.MiniAppTenantBindingDTO;
import com.aryn.cloud.user.api.entity.SocialAccount;
import com.aryn.cloud.user.api.entity.SocialUser;
import com.aryn.cloud.user.api.remote.RemoteMiniAppTenantService;
import com.aryn.cloud.user.service.ISocialUserService;
import com.aryn.cloud.user.service.IUserInfoService;
import com.aryn.cloud.user.service.MiniAppBindingResolver;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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

	private final ISocialUserService socialUserService;

	private final IUserInfoService userInfoService;

	@Override
	public MiniAppTenantBindingDTO requireBinding(String appId, String expectedTenantId) {
		return requireBinding(appId, expectedTenantId, OpenPlatformTypeEnum.WX_MA.getCode());
	}

	@Override
	public MiniAppTenantBindingDTO requireBinding(String appId, String expectedTenantId, String platformType) {
		SocialAccount account = miniAppBindingResolver.requireBinding(appId, expectedTenantId, platformType);
		MiniAppTenantBindingDTO dto = new MiniAppTenantBindingDTO();
		dto.setAppId(account.getAppId());
		dto.setTenantId(account.getTenantId());
		dto.setPlatformType(account.getType());
		dto.setIdentityMode(identityMode(account));
		dto.setAccountId(account.getId());
		dto.setConfigFingerprint(String.valueOf(account.getUpdateTime()));
		dto.setReady(Boolean.TRUE);
		return dto;
	}

	@Override
	public boolean verifyBuyerIdentity(MiniAppBuyerIdentityVerifyDTO request) {
		if (request == null || !StringUtils.hasText(request.getTenantId())
				|| !StringUtils.hasText(request.getAppId()) || !StringUtils.hasText(request.getBuyerIdentity())
				|| !StringUtils.hasText(request.getMallUserId())) {
			return false;
		}
		boolean alipayMini = OpenPlatformTypeEnum.ALI_MINI.getCode().equals(request.getPlatformType());
		boolean wechatMini = OpenPlatformTypeEnum.WX_MA.getCode().equals(request.getPlatformType());
		if (!alipayMini && !wechatMini) {
			return false;
		}
		MiniAppIdentityModeEnum mode = MiniAppIdentityModeEnum.fromCode(request.getIdentityMode());
		if (alipayMini && mode == null) {
			return false;
		}
		if (wechatMini) {
			if (mode != null && mode != MiniAppIdentityModeEnum.OPEN_ID) {
				return false;
			}
			mode = MiniAppIdentityModeEnum.OPEN_ID;
		}
		String tenantId = request.getTenantId().trim();
		String previousTenantId = ArynTenantContextHolder.getTenantId();
		try {
			ArynTenantContextHolder.setTenantId(tenantId);
			long bindings = socialUserService.count(Wrappers.<SocialUser>lambdaQuery()
				.eq(SocialUser::getTenantId, tenantId)
				.eq(SocialUser::getPlatformType, request.getPlatformType())
				.eq(SocialUser::getAppId, request.getAppId().trim())
				.eq(SocialUser::getIdentityMode, mode.getCode())
				.eq(SocialUser::getOpenId, request.getBuyerIdentity().trim())
				.eq(SocialUser::getMallUserId, request.getMallUserId().trim()));
			if (bindings != 1) {
				return false;
			}
			long users = userInfoService.count(Wrappers.<UserInfo>lambdaQuery()
				.eq(UserInfo::getId, request.getMallUserId().trim())
				.eq(UserInfo::getTenantId, tenantId));
			return users == 1;
		}
		finally {
			if (StringUtils.hasText(previousTenantId)) {
				ArynTenantContextHolder.setTenantId(previousTenantId);
			}
			else {
				ArynTenantContextHolder.removeTenantId();
			}
		}
	}

	private String identityMode(SocialAccount account) {
		if (OpenPlatformTypeEnum.ALI_MINI.getCode().equals(account.getType())) {
			return account.getIdentityMode();
		}
		return MiniAppIdentityModeEnum.OPEN_ID.getCode();
	}

}
