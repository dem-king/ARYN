/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 */

package com.aryn.cloud.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.user.api.entity.SocialAccount;
import com.aryn.cloud.user.api.vo.SocialAccountVO;

/**
 * 三方账号
 *
 * @author 雨滴kian
 * @date 2026-04-05 00:20:56
 */
public interface ISocialAccountService extends IService<SocialAccount> {

	SocialAccountVO selectByAppId(String appId);

	SocialAccountVO getSocialAccountById(String id);

	IPage<SocialAccountVO> getPage(Page page, SocialAccount socialAccount);

	/**
	 * 新增三方账号（同步内存配置）
	 */
	boolean saveSocialAccount(SocialAccount socialAccount);

	/**
	 * 修改三方账号（密钥未变更时不覆盖；同步内存配置）
	 */
	Boolean updateSocialAccountById(SocialAccount socialAccount);

	/**
	 * 删除三方账号（同步内存配置）
	 */
	boolean removeSocialAccountById(String id);

}
