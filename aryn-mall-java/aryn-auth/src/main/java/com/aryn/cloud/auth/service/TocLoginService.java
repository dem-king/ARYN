/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 */
package com.aryn.cloud.auth.service;

import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.crypto.digest.BCrypt;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.enums.OpenPlatformTypeEnum;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.user.api.dto.SocialUserBindDTO;
import com.aryn.cloud.user.api.dto.SocialUserUnbindDTO;
import com.aryn.cloud.user.api.dto.UserLoginReqDTO;
import com.aryn.cloud.user.api.entity.SocialUser;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.remote.RemoteSocialUserService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class TocLoginService {

	@DubboReference
	private final RemoteSocialUserService remoteSocialUserService;

	@DubboReference
	private final RemoteMallUserService remoteMallUserService;

	public SaTokenInfo maPhoneLogin(UserLoginReqDTO userLoginReqDTO) {
		// 解密手机号
		String phone = remoteSocialUserService.getPhoneNumberInfo(userLoginReqDTO);
		if (!StringUtils.hasText(phone)) {
			throw new IllegalArgumentException("login failed");
		}
		// 通过手机号查询商城用户，不存在创建新用户无需用户注册
		UserInfo userInfo = remoteMallUserService.getInfoByPhone(phone, userLoginReqDTO.getPlatformType());
		if (Objects.isNull(userInfo)) {
			throw new IllegalArgumentException("login failed");
		}
		// 获取三方用户
		SocialUser socialUser = remoteSocialUserService.socialLogin(userLoginReqDTO);
		if (Objects.isNull(socialUser)) {
			throw new IllegalArgumentException("login failed!");
		}
		bindOpenUser(socialUser, userInfo.getId(), userLoginReqDTO);

		ArynUser hxUser = new ArynUser();
		hxUser.setUserId(userInfo.getId());
		hxUser.setOpenId(socialUser.getOpenId());
		hxUser.setTenantId(ArynTenantContextHolder.getTenantId());
		hxUser.setUsername(userInfo.getNickname());
		SecurityUtils.loginByDevice(hxUser, DeviceTypeEnum.TOC);
		return StpUtil.getTokenInfo();
	}

	public SaTokenInfo smsLogin(UserLoginReqDTO userLoginReqDTO) {

		// 通过手机号查询商城用户，不存在创建新用户无需用户注册
		UserInfo userInfo = remoteMallUserService.getInfoByPhone(userLoginReqDTO.getPhone(),
				userLoginReqDTO.getPlatformType());
		if (Objects.isNull(userInfo)) {
			throw new IllegalArgumentException("login failed");
		}

		ArynUser hxUser = new ArynUser();
		if (userLoginReqDTO.getPlatformType().equals(OpenPlatformTypeEnum.WX_MA.getCode())) {
			// 获取三方用户
			SocialUser socialUser = remoteSocialUserService.socialLogin(userLoginReqDTO);
			if (Objects.isNull(socialUser)) {
				throw new IllegalArgumentException("login failed!");
			}
			hxUser.setOpenId(socialUser.getOpenId());
			bindOpenUser(socialUser, userInfo.getId(), userLoginReqDTO);
		}
		hxUser.setUserId(userInfo.getId());
		hxUser.setTenantId(ArynTenantContextHolder.getTenantId());
		hxUser.setUsername(userInfo.getNickname());
		SecurityUtils.loginByDevice(hxUser, DeviceTypeEnum.TOC);

		return StpUtil.getTokenInfo();
	}

	public SaTokenInfo passwordLogin(UserLoginReqDTO userLoginReqDTO) {
		// 通过手机号查询商城用户
		UserInfoVO userInfo = remoteMallUserService.getUserByPhone(userLoginReqDTO.getPhone());
		// 统一返回"账号或密码错误"，不区分用户是否存在，防止手机号探测
		if (Objects.isNull(userInfo)) {
			throw new ArynBusinessException("账号或密码错误");
		}
		if (!StringUtils.hasText(userInfo.getPassword())) {
			throw new ArynBusinessException("账号或密码错误");
		}
		if (!BCrypt.checkpw(userLoginReqDTO.getPassword(), userInfo.getPassword())) {
			throw new ArynBusinessException("账号或密码错误");
		}
		ArynUser hxUser = new ArynUser();
		if (userLoginReqDTO.getPlatformType().equals(OpenPlatformTypeEnum.WX_MA.getCode())) {
			// 获取三方用户
			SocialUser socialUser = remoteSocialUserService.socialLogin(userLoginReqDTO);
			if (Objects.isNull(socialUser)) {
				throw new IllegalArgumentException("login failed!");
			}
			hxUser.setOpenId(socialUser.getOpenId());
			bindOpenUser(socialUser, userInfo.getId(), userLoginReqDTO);
		}
		hxUser.setUserId(userInfo.getId());
		hxUser.setTenantId(userInfo.getTenantId());
		hxUser.setUsername(userInfo.getNickname());
		SecurityUtils.loginByDevice(hxUser, DeviceTypeEnum.TOC);

		return StpUtil.getTokenInfo();
	}

	public Object maLogin(UserLoginReqDTO userLoginReqDTO) {
		SocialUser socialUser = remoteSocialUserService.socialLogin(userLoginReqDTO);
		if (Objects.isNull(socialUser)) {
			throw new IllegalArgumentException("login failed!");
		}

		String userId = socialUser.getMallUserId();
		if (!StringUtils.hasText(userId)) {
			// 创建用户
			UserInfo userInfo = remoteMallUserService.getUserByOpenId(socialUser.getOpenId(),
					userLoginReqDTO.getPlatformType());
			// 三方用户跟平台用户绑定
			userId = userInfo.getId();
			bindOpenUser(socialUser, userInfo.getId(), userLoginReqDTO);
		}
		return loginMallUser(userId, socialUser.getOpenId(), socialUser.getTenantId());

	}

	public void logout(UserLoginReqDTO userLoginReqDTO) {
		// 小程序解绑商城用户
		if (OpenPlatformTypeEnum.WX_MA.getCode().equals(userLoginReqDTO.getPlatformType())) {
			unbindOpenUser(SecurityUtils.getOpenId(), SecurityUtils.getUserId(), userLoginReqDTO);
		}
		StpUtil.logout();
	}

	private Object loginMallUser(String userId, String openid, String tenantId) {
		ArynUser hxUser = new ArynUser();
		hxUser.setUserId(userId);
		hxUser.setOpenId(openid);
		hxUser.setTenantId(tenantId);
		hxUser.setUsername(openid);

		SecurityUtils.loginByDevice(hxUser, DeviceTypeEnum.TOC);
		return StpUtil.getTokenInfo();
	}

	private void bindOpenUser(SocialUser socialUser, String userId, UserLoginReqDTO userLoginReqDTO) {
		SocialUserBindDTO dto = new SocialUserBindDTO();
		dto.setId(socialUser.getId());
		dto.setMallUserId(userId);
		dto.setAppId(userLoginReqDTO.getAppId());
		if (!remoteSocialUserService.bindUserId(dto)) {
			throw new IllegalArgumentException("bind user failed");
		}
	}

	private void unbindOpenUser(String openId, String userId, UserLoginReqDTO userLoginReqDTO) {
		SocialUserUnbindDTO dto = new SocialUserUnbindDTO();
		dto.setOpenId(openId);
		dto.setMallUserId(userId);
		dto.setAppId(userLoginReqDTO.getAppId());
		if (!remoteSocialUserService.unbindUserId(dto)) {
			throw new IllegalArgumentException("unbind user failed");
		}
	}

}
