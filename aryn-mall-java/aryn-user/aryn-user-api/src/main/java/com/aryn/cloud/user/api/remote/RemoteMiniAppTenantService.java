package com.aryn.cloud.user.api.remote;

import com.aryn.cloud.user.api.dto.MiniAppBuyerIdentityVerifyDTO;
import com.aryn.cloud.user.api.dto.MiniAppTenantBindingDTO;

/**
 * 小程序租户绑定权威校验。
 *
 * 登录前必须校验请求携带的 tenant-id 与该微信 AppID 在 social_account 中的权威归属一致；
 * 多实例场景每次登录权威查库并刷新消费实例缓存，不信任进程内旧缓存。
 *
 * @author aryn
 * @since 2026-10-10
 */
public interface RemoteMiniAppTenantService {

	/**
	 * 校验 AppID 与期望租户的权威绑定关系。
	 * @param appId 小程序 AppID（来自请求头 app-id）
	 * @param expectedTenantId 期望租户（来自原始请求头 tenant-id，不取 ThreadLocal）
	 * @return 绑定信息（不含任何机密字段）
	 * @throws com.aryn.cloud.common.security.handler.ArynBusinessException 配置不存在、重复或与期望租户不一致（403）
	 */
	MiniAppTenantBindingDTO requireBinding(String appId, String expectedTenantId);

	/**
	 * 校验 AppID 与期望租户、平台类型的权威绑定关系。
	 * @param appId 小程序 AppID（来自请求头 app-id）
	 * @param expectedTenantId 期望租户（来自原始请求头 tenant-id，不取 ThreadLocal）
	 * @param platformType WX_MA / ALI_MINI
	 * @return 绑定信息（含身份模式，不含任何机密字段）
	 */
	MiniAppTenantBindingDTO requireBinding(String appId, String expectedTenantId, String platformType);

	/**
	 * 校验支付宝小程序买家身份确属当前租户/应用/身份模式的商城用户。
	 *
	 * @param request 买家身份与商城用户映射
	 * @return true 仅当 social_user 与 user_info 均命中同一租户
	 */
	boolean verifyBuyerIdentity(MiniAppBuyerIdentityVerifyDTO request);

}
