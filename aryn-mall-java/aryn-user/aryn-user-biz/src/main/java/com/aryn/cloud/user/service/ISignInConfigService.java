package com.aryn.cloud.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.user.api.entity.SignInConfig;

/**
 * 签到配置
 *
 * @author 雨滴kian
 */
public interface ISignInConfigService extends IService<SignInConfig> {

	IPage<SignInConfig> getPage(Page page, SignInConfig signInConfig);

	SignInConfig getDetailById(String id);

	boolean saveConfig(SignInConfig signInConfig);

	boolean updateConfig(SignInConfig signInConfig);

	boolean deleteConfig(String id);

}
