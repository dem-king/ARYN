package com.aryn.cloud.user.api.remote;

import com.aryn.cloud.user.api.dto.SocialUserBindDTO;
import com.aryn.cloud.user.api.dto.SocialUserUnbindDTO;
import com.aryn.cloud.user.api.dto.UserLoginReqDTO;
import com.aryn.cloud.user.api.entity.SocialUser;

public interface RemoteSocialUserService {

	/**
	 * 三方平台账号登录：微信login
	 * @param userLoginReqDTO 登录信息
	 * @return 三方用户信息
	 */
	SocialUser socialLogin(UserLoginReqDTO userLoginReqDTO);

	/**
	 * 支付宝小程序登录：用 my.getAuthCode 授权码换取买家身份并落库。
	 *
	 * <p>身份模式必须由 social_account 显式配置，且与支付身份版本一致；
	 * 不接受、不猜测、不复用微信 openid。
	 *
	 * @param userLoginReqDTO appId、requestTenantId、authCode、platformType=ALI_MINI
	 * @return 已绑定当前租户的三方用户
	 */
	SocialUser aliMiniLogin(UserLoginReqDTO userLoginReqDTO);

	boolean bindUserId(SocialUserBindDTO dto);

	boolean unbindUserId(SocialUserUnbindDTO dto);

	String getPhoneNumberInfo(UserLoginReqDTO userLoginReqDTO);

	/**
	 * 按商城用户ID查询微信小程序 openid（C 端订阅消息使用）。
	 * @return openid；未绑定返回 null
	 */
	String getOpenidByMallUserId(String tenantId, String mallUserId);

}
