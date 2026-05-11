/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 */

package com.aryn.cloud.user.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.user.api.entity.SocialAccount;
import com.aryn.cloud.user.api.vo.SocialAccountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 三方平台账号
 *
 * @author 雨滴kian
 * @date 2026-04-05 00:20:56
 */
@Mapper
public interface SocialAccountMapper extends BaseMapper<SocialAccount> {

	@InterceptorIgnore(tenantLine = "true")
	SocialAccountVO selectByAppId(String appId);

	SocialAccountVO selectSocialAccountById(@Param("id") String id);

	IPage<SocialAccountVO> selectSocialAccountPage(Page page, @Param("model") SocialAccount socialAccount);

}
