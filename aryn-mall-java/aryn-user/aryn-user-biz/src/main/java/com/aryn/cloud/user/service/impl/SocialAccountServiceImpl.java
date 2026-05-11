/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 */

package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.desensitization.KeyDesensitization;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.SocialAccount;
import com.aryn.cloud.user.api.vo.SocialAccountVO;
import com.aryn.cloud.user.config.WxMiniAppConfigCache;
import com.aryn.cloud.user.mapper.SocialAccountMapper;
import com.aryn.cloud.user.service.ISocialAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 三方账号
 *
 * @author 雨滴kian
 * @date 2026-04-05 00:20:56
 */
@Service
@RequiredArgsConstructor
public class SocialAccountServiceImpl extends ServiceImpl<SocialAccountMapper, SocialAccount>
		implements ISocialAccountService {

	private final KeyDesensitization keyDesensitization = new KeyDesensitization();

	private final WxMiniAppConfigCache configCache;

	@Override
	@Cacheable(value = CacheConstants.SOCIAL_ACCOUNT_CACHE, key = "'app:' + #appId", unless = "#result == null")
	public SocialAccountVO selectByAppId(String appId) {
		return baseMapper.selectByAppId(appId);
	}

	@Override
	@Cacheable(value = CacheConstants.SOCIAL_ACCOUNT_CACHE, key = "#id", unless = "#result == null")
	public SocialAccountVO getSocialAccountById(String id) {
		return baseMapper.selectSocialAccountById(id);
	}

	@Override
	public IPage<SocialAccountVO> getPage(Page page, SocialAccount socialAccount) {
		return baseMapper.selectSocialAccountPage(page, socialAccount);
	}

	@Override
	public boolean saveSocialAccount(SocialAccount socialAccount) {
		boolean saved = this.save(socialAccount);
		if (saved) {
			SocialAccount reloaded = baseMapper.selectById(socialAccount.getId());
			if (reloaded != null) {
				configCache.updateConfig(reloaded);
			}
		}
		return saved;
	}

	@Override
	@CacheEvict(value = CacheConstants.SOCIAL_ACCOUNT_CACHE, allEntries = true)
	public Boolean updateSocialAccountById(SocialAccount socialAccount) {
		SocialAccount target = baseMapper.selectById(socialAccount.getId());
		if (Objects.isNull(target)) {
			throw new ArynBusinessException("三方账号不存在");
		}
		String oldAppId = target.getAppId();
		if (target.getAppSecret() != null
				&& keyDesensitization.serialize(target.getAppSecret()).equals(socialAccount.getAppSecret())) {
			socialAccount.setAppSecret(null);
		}
		boolean updated = this.updateById(socialAccount);
		if (updated) {
			SocialAccount reloaded = baseMapper.selectById(socialAccount.getId());
			if (reloaded != null) {
				if (socialAccount.getAppId() != null && !socialAccount.getAppId().equals(oldAppId)) {
					configCache.removeByAppId(oldAppId);
				}
				configCache.updateConfig(reloaded);
			}
		}
		return updated;
	}

	@Override
	@CacheEvict(value = CacheConstants.SOCIAL_ACCOUNT_CACHE, allEntries = true)
	public boolean removeSocialAccountById(String id) {
		SocialAccount target = baseMapper.selectById(id);
		if (Objects.isNull(target)) {
			throw new ArynBusinessException("三方账号不存在");
		}
		String appId = target.getAppId();
		baseMapper.deleteById(id);
		configCache.removeByAppId(appId);
		return true;
	}

}
