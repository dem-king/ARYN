package com.aryn.cloud.user.service;

import com.aryn.cloud.common.core.enums.MiniAppIdentityModeEnum;
import com.aryn.cloud.common.core.enums.OpenPlatformTypeEnum;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.SocialAccount;
import com.aryn.cloud.user.config.WxMiniAppConfigCache;
import com.aryn.cloud.user.mapper.SocialAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 小程序 AppID ↔ 租户绑定权威解析。
 *
 * 每次调用直查 social_account（绕过租户过滤与 Spring 查询缓存），是登录前身份校验的
 * 唯一权威入口；进程内 WxMiniAppConfigCache 不能用于安全判定。规则 fail-closed：
 * 0 条配置、多条配置、租户不一致、平台类型不匹配、WX secret 缺失、ALI_MINI 身份模式
 * 缺失一律 403 拒绝，绝不自动纠正为另一租户或另一种身份。
 *
 * @author aryn
 * @since 2026-10-10
 */
@Service
@RequiredArgsConstructor
public class MiniAppBindingResolver {

	private final SocialAccountMapper socialAccountMapper;

	private final WxMiniAppConfigCache configCache;

	/**
	 * 微信小程序绑定校验（兼容既有调用方）。
	 */
	public SocialAccount requireBinding(String appId, String expectedTenantId) {
		return requireBinding(appId, expectedTenantId, OpenPlatformTypeEnum.WX_MA.getCode());
	}

	/**
	 * 校验 AppID 与期望租户、平台类型一致。WX_MA 只更新微信 SDK 缓存；
	 * ALI_MINI 只做权威校验，不写入微信配置缓存。
	 */
	public SocialAccount requireBinding(String appId, String expectedTenantId, String platformType) {
		if (!StringUtils.hasText(appId)) {
			throw new ArynBusinessException(403, "缺少小程序 AppID，拒绝登录");
		}
		if (!StringUtils.hasText(expectedTenantId)) {
			throw new ArynBusinessException(403, "缺少租户标识，拒绝登录");
		}
		String normalizedPlatform = normalizePlatform(platformType);
		List<SocialAccount> accounts = socialAccountMapper.selectValidByAppIdAndType(appId, normalizedPlatform);
		if (accounts.isEmpty() || accounts.size() > 1) {
			throw new ArynBusinessException(403, "小程序与租户配置不一致");
		}
		SocialAccount account = accounts.get(0);
		if (!expectedTenantId.equals(account.getTenantId())) {
			throw new ArynBusinessException(403, "小程序与租户配置不一致");
		}
		if (OpenPlatformTypeEnum.WX_MA.getCode().equals(normalizedPlatform)) {
			if (!StringUtils.hasText(account.getAppSecret())) {
				throw new ArynBusinessException(403, "小程序配置不完整");
			}
			configCache.updateConfig(copyOf(account));
		}
		else {
			MiniAppIdentityModeEnum mode = MiniAppIdentityModeEnum.fromCode(account.getIdentityMode());
			if (mode == null) {
				throw new ArynBusinessException(403, "支付宝小程序身份模式未配置");
			}
			account.setIdentityMode(mode.getCode());
		}
		return account;
	}

	private String normalizePlatform(String platformType) {
		if (!StringUtils.hasText(platformType)) {
			throw new ArynBusinessException(403, "缺少小程序平台类型，拒绝登录");
		}
		String value = platformType.trim();
		if (OpenPlatformTypeEnum.WX_MA.getCode().equals(value)
				|| OpenPlatformTypeEnum.ALI_MINI.getCode().equals(value)) {
			return value;
		}
		throw new ArynBusinessException(403, "不支持的小程序平台");
	}

	private SocialAccount copyOf(SocialAccount source) {
		SocialAccount copy = new SocialAccount();
		copy.setId(source.getId());
		copy.setType(source.getType());
		copy.setAppId(source.getAppId());
		copy.setAppSecret(source.getAppSecret());
		copy.setIdentityMode(source.getIdentityMode());
		copy.setCreateTime(source.getCreateTime());
		copy.setUpdateTime(source.getUpdateTime());
		copy.setDelFlag(source.getDelFlag());
		copy.setCreateBy(source.getCreateBy());
		copy.setUpdateBy(source.getUpdateBy());
		copy.setTenantId(source.getTenantId());
		return copy;
	}

}
