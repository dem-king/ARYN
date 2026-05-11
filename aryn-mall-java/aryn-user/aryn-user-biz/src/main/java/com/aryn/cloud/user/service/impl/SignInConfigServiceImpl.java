package com.aryn.cloud.user.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.SignInConfig;
import com.aryn.cloud.user.mapper.SignInConfigMapper;
import com.aryn.cloud.user.service.ISignInConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 签到配置
 *
 * @author 雨滴kian
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SignInConfigServiceImpl extends ServiceImpl<SignInConfigMapper, SignInConfig>
		implements ISignInConfigService {

	@Override
	public IPage<SignInConfig> getPage(Page page, SignInConfig signInConfig) {
		return this.page(page,
				Wrappers.<SignInConfig>lambdaQuery().orderByAsc(SignInConfig::getConsecutiveDay));
	}

	@Override
	public SignInConfig getDetailById(String id) {
		return this.getById(id);
	}

	@Override
	public boolean saveConfig(SignInConfig signInConfig) {
		// 校验连续签到天数是否重复
		long count = this.count(Wrappers.<SignInConfig>lambdaQuery()
				.eq(SignInConfig::getConsecutiveDay, signInConfig.getConsecutiveDay()));
		if (count > 0) {
			throw new ArynBusinessException("相同连续签到天数的配置已存在");
		}
		return this.save(signInConfig);
	}

	@Override
	public boolean updateConfig(SignInConfig signInConfig) {
		// 校验连续签到天数是否重复（排除自身）
		long count = this.count(Wrappers.<SignInConfig>lambdaQuery()
				.eq(SignInConfig::getConsecutiveDay, signInConfig.getConsecutiveDay())
				.ne(SignInConfig::getId, signInConfig.getId()));
		if (count > 0) {
			throw new ArynBusinessException("相同连续签到天数的配置已存在");
		}
		return this.updateById(signInConfig);
	}

	@Override
	public boolean deleteConfig(String id) {
		return this.removeById(id);
	}

}
