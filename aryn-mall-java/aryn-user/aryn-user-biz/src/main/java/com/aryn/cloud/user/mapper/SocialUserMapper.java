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
import com.aryn.cloud.user.api.entity.SocialUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 三方平台用户
 *
 * @author 雨滴kian
 * @date 2026-04-05 00:21:22
 */
@Mapper
public interface SocialUserMapper extends BaseMapper<SocialUser> {

	/**
	 * 跨租户按主键反查三方用户，仅用于绑定阶段恢复租户上下文。
	 * <p>
	 * Dubbo injvm 在服务提供方返回时会清理调用方线程上下文，此时带租户条件的 getById 会查不到记录，
	 * 需要先在任意租户下按主键定位到这条三方用户记录，再由调用方显式恢复其 tenant_id。
	 */
	@InterceptorIgnore(tenantLine = "true")
	SocialUser selectByIdInAnyTenant(@Param("id") String id);

	/**
	 * 跨租户按 appId + openId + mallUserId 反查三方用户，仅用于解绑阶段恢复租户上下文。
	 * <p>
	 * 单靠 appId 无法定位唯一记录：同一小程序下每个 openId 各有一条三方用户记录。必须带上解绑请求
	 * 里已有的 openId 与 mallUserId，才能与紧邻的带租户条件查询语义等价。
	 */
	@InterceptorIgnore(tenantLine = "true")
	SocialUser selectByAppIdAndOpenIdInAnyTenant(@Param("appId") String appId, @Param("openId") String openId,
			@Param("mallUserId") String mallUserId);

}
