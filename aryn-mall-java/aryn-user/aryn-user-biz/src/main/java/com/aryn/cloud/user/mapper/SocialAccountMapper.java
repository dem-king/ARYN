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

	/**
	 * 权威绑定查询：绕过租户过滤、绕过 Spring 查询缓存，每次登录/绑定前直查。
	 * 返回 List 而非单条：同一 AppID 出现多条有效 WX_MA 配置时由 Resolver fail-closed 拒绝。
	 */
	@InterceptorIgnore(tenantLine = "true")
	java.util.List<SocialAccount> selectValidWxMaByAppId(@Param("appId") String appId);

	/**
	 * 后台维护用全局重复预检：同 AppID（WX_MA、有效）下排除自身后的条数。
	 */
	@InterceptorIgnore(tenantLine = "true")
	long countValidWxMaByAppIdExcluding(@Param("appId") String appId, @Param("excludeId") String excludeId);

	SocialAccountVO selectSocialAccountById(@Param("id") String id);

	IPage<SocialAccountVO> selectSocialAccountPage(Page page, @Param("model") SocialAccount socialAccount);

}
