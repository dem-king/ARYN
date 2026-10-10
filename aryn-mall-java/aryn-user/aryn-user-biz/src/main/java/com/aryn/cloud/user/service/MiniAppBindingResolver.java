package com.aryn.cloud.user.service;

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
 * 0 条配置、多条配置、租户不一致、secret 缺失一律 403 拒绝，绝不自动纠正为另一租户。
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
	 * 校验 AppID 与期望租户一致。校验成功后把权威记录刷新进本实例 SDK 配置缓存
	 * （独立副本，避免调用方持有可变引用），保证本次微信 RPC 消费与判定同源。
	 */
	public SocialAccount requireBinding(String appId, String expectedTenantId) {
		if (!StringUtils.hasText(appId)) {
			throw new ArynBusinessException(403, "缺少小程序 AppID，拒绝登录");
		}
		if (!StringUtils.hasText(expectedTenantId)) {
			throw new ArynBusinessException(403, "缺少租户标识，拒绝登录");
		}
		List<SocialAccount> accounts = socialAccountMapper.selectValidWxMaByAppId(appId);
		if (accounts.isEmpty()) {
			throw new ArynBusinessException(403, "小程序与租户配置不一致");
		}
		if (accounts.size() > 1) {
			throw new ArynBusinessException(403, "小程序与租户配置不一致");
		}
		SocialAccount account = accounts.get(0);
		if (!expectedTenantId.equals(account.getTenantId())) {
			throw new ArynBusinessException(403, "小程序与租户配置不一致");
		}
		if (!StringUtils.hasText(account.getAppSecret())) {
			throw new ArynBusinessException(403, "小程序配置不完整");
		}
		configCache.updateConfig(copyOf(account));
		return account;
	}

	private SocialAccount copyOf(SocialAccount source) {
		SocialAccount copy = new SocialAccount();
		copy.setId(source.getId());
		copy.setType(source.getType());
		copy.setAppId(source.getAppId());
		copy.setAppSecret(source.getAppSecret());
		copy.setCreateTime(source.getCreateTime());
		copy.setUpdateTime(source.getUpdateTime());
		copy.setDelFlag(source.getDelFlag());
		copy.setCreateBy(source.getCreateBy());
		copy.setUpdateBy(source.getUpdateBy());
		copy.setTenantId(source.getTenantId());
		return copy;
	}

}
