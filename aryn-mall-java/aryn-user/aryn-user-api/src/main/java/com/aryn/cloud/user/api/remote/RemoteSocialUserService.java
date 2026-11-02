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

	boolean bindUserId(SocialUserBindDTO dto);

	boolean unbindUserId(SocialUserUnbindDTO dto);

	String getPhoneNumberInfo(UserLoginReqDTO userLoginReqDTO);

	/**
	 * 按商城用户ID查询微信小程序 openid（C 端订阅消息使用）。
	 * @return openid；未绑定返回 null
	 */
	String getOpenidByMallUserId(String tenantId, String mallUserId);

}
