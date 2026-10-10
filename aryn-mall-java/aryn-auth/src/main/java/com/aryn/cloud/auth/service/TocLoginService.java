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
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Objects;

/**
 * C 端登录流程。
 *
 * P0-B 身份边界：任何查商城用户、创建用户、调用手机号解密之前，先执行
 * AppID↔租户绑定权威校验（{@link LoginTenantGuard}），随后把已验证租户写入
 * ThreadLocal，整个流程内不允许再切换到其它租户；finally 恢复进入前快照，
 * 兼容 Boot injvm 与 cloud RPC 两种上下文语义。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TocLoginService {

	@DubboReference
	private final RemoteSocialUserService remoteSocialUserService;

	@DubboReference
	private final RemoteMallUserService remoteMallUserService;

	private final LoginTenantGuard loginTenantGuard;

	public SaTokenInfo maPhoneLogin(UserLoginReqDTO userLoginReqDTO) {
		// /ma/phone/login 固定微信小程序语义：平台缺失/错误直接拒绝
		assertWxMaPlatform(userLoginReqDTO);
		String verifiedTenantId = loginTenantGuard.requireVerifiedTenant(
				userLoginReqDTO.getAppId(), userLoginReqDTO.getRequestTenantId());
		String previousTenantId = ArynTenantContextHolder.getTenantId();
		ArynTenantContextHolder.setTenantId(verifiedTenantId);
		try {
			// 解密手机号（user-biz 内部再次权威重查绑定）
			String phone = remoteSocialUserService.getPhoneNumberInfo(userLoginReqDTO);
			if (!StringUtils.hasText(phone)) {
				throw new IllegalArgumentException("login failed");
			}
			// 通过手机号查询商城用户，不存在创建新用户无需用户注册（租户过滤保证同租户）
			UserInfo userInfo = remoteMallUserService.getInfoByPhone(phone, userLoginReqDTO.getPlatformType());
			if (Objects.isNull(userInfo)) {
				throw new IllegalArgumentException("login failed");
			}
			assertUserTenant(userInfo.getTenantId(), verifiedTenantId, "商城用户");
			// 获取三方用户
			SocialUser socialUser = remoteSocialUserService.socialLogin(userLoginReqDTO);
			assertSocialUserTenant(socialUser, verifiedTenantId);
			bindOpenUser(socialUser, userInfo.getId(), userLoginReqDTO, verifiedTenantId);

			ArynUser hxUser = new ArynUser();
			hxUser.setUserId(userInfo.getId());
			hxUser.setOpenId(socialUser.getOpenId());
			hxUser.setTenantId(verifiedTenantId);
			hxUser.setUsername(userInfo.getNickname());
			SecurityUtils.loginByDevice(hxUser, DeviceTypeEnum.TOC);
			return StpUtil.getTokenInfo();
		}
		finally {
			ArynTenantContextHolder.setTenantId(previousTenantId);
		}
	}

	public SaTokenInfo smsLogin(UserLoginReqDTO userLoginReqDTO) {
		boolean wxMa = OpenPlatformTypeEnum.WX_MA.getCode().equals(userLoginReqDTO.getPlatformType());
		assertPlatformMatchesAppId(userLoginReqDTO);
		// WX_MA 先权威校验绑定再查用户；H5/APP 保留既有租户登录分支
		String verifiedTenantId = wxMa
				? loginTenantGuard.requireVerifiedTenant(userLoginReqDTO.getAppId(), userLoginReqDTO.getRequestTenantId())
				: null;
		String previousTenantId = ArynTenantContextHolder.getTenantId();
		if (verifiedTenantId != null) {
			ArynTenantContextHolder.setTenantId(verifiedTenantId);
		}
		try {
			// 通过手机号查询商城用户，不存在创建新用户无需用户注册
			UserInfo userInfo = remoteMallUserService.getInfoByPhone(userLoginReqDTO.getPhone(),
					userLoginReqDTO.getPlatformType());
			if (Objects.isNull(userInfo)) {
				throw new IllegalArgumentException("login failed");
			}

			ArynUser hxUser = new ArynUser();
			String tenantId = verifiedTenantId != null ? verifiedTenantId : ArynTenantContextHolder.getTenantId();
			if (wxMa) {
				// 获取三方用户
				SocialUser socialUser = remoteSocialUserService.socialLogin(userLoginReqDTO);
				assertSocialUserTenant(socialUser, verifiedTenantId);
				hxUser.setOpenId(socialUser.getOpenId());
				assertUserTenant(userInfo.getTenantId(), verifiedTenantId, "商城用户");
				bindOpenUser(socialUser, userInfo.getId(), userLoginReqDTO, verifiedTenantId);
			}
			hxUser.setUserId(userInfo.getId());
			hxUser.setTenantId(tenantId);
			hxUser.setUsername(userInfo.getNickname());
			SecurityUtils.loginByDevice(hxUser, DeviceTypeEnum.TOC);

			return StpUtil.getTokenInfo();
		}
		finally {
			ArynTenantContextHolder.setTenantId(previousTenantId);
		}
	}

	public SaTokenInfo passwordLogin(UserLoginReqDTO userLoginReqDTO) {
		boolean wxMa = OpenPlatformTypeEnum.WX_MA.getCode().equals(userLoginReqDTO.getPlatformType());
		assertPlatformMatchesAppId(userLoginReqDTO);
		String verifiedTenantId = wxMa
				? loginTenantGuard.requireVerifiedTenant(userLoginReqDTO.getAppId(), userLoginReqDTO.getRequestTenantId())
				: null;
		String previousTenantId = ArynTenantContextHolder.getTenantId();
		if (verifiedTenantId != null) {
			ArynTenantContextHolder.setTenantId(verifiedTenantId);
		}
		try {
			// 通过手机号查询商城用户
			UserInfoVO userInfo = remoteMallUserService.getUserByPhone(userLoginReqDTO.getPhone());
			if (Objects.isNull(userInfo)) {
				throw new IllegalArgumentException("请先注册");
			}
			if (!StringUtils.hasText(userInfo.getPassword())) {
				throw new RuntimeException("账号或密码错误");
			}
			if (!BCrypt.checkpw(userLoginReqDTO.getPassword(), userInfo.getPassword())) {
				throw new RuntimeException("账号或密码错误");
			}
			ArynUser hxUser = new ArynUser();
			if (wxMa) {
				// 获取三方用户
				SocialUser socialUser = remoteSocialUserService.socialLogin(userLoginReqDTO);
				assertSocialUserTenant(socialUser, verifiedTenantId);
				hxUser.setOpenId(socialUser.getOpenId());
				assertUserTenant(userInfo.getTenantId(), verifiedTenantId, "商城用户");
				bindOpenUser(socialUser, userInfo.getId(), userLoginReqDTO, verifiedTenantId);
				hxUser.setTenantId(verifiedTenantId);
			}
			hxUser.setUserId(userInfo.getId());
			if (!StringUtils.hasText(hxUser.getTenantId())) {
				hxUser.setTenantId(userInfo.getTenantId());
			}
			hxUser.setUsername(userInfo.getNickname());
			SecurityUtils.loginByDevice(hxUser, DeviceTypeEnum.TOC);

			return StpUtil.getTokenInfo();
		}
		finally {
			ArynTenantContextHolder.setTenantId(previousTenantId);
		}
	}

	public Object maLogin(UserLoginReqDTO userLoginReqDTO) {
		assertWxMaPlatform(userLoginReqDTO);
		String verifiedTenantId = loginTenantGuard.requireVerifiedTenant(
				userLoginReqDTO.getAppId(), userLoginReqDTO.getRequestTenantId());
		String previousTenantId = ArynTenantContextHolder.getTenantId();
		ArynTenantContextHolder.setTenantId(verifiedTenantId);
		try {
			SocialUser socialUser = remoteSocialUserService.socialLogin(userLoginReqDTO);
			assertSocialUserTenant(socialUser, verifiedTenantId);

			String userId = socialUser.getMallUserId();
			if (!StringUtils.hasText(userId)) {
				// 创建用户
				UserInfo userInfo = remoteMallUserService.getUserByOpenId(socialUser.getOpenId(),
						userLoginReqDTO.getPlatformType());
				assertUserTenant(userInfo.getTenantId(), verifiedTenantId, "商城用户");
				// 三方用户跟平台用户绑定
				userId = userInfo.getId();
				bindOpenUser(socialUser, userInfo.getId(), userLoginReqDTO, verifiedTenantId);
			}
			return loginMallUser(userId, socialUser.getOpenId(), verifiedTenantId);
		}
		finally {
			ArynTenantContextHolder.setTenantId(previousTenantId);
		}
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

	private void assertWxMaPlatform(UserLoginReqDTO userLoginReqDTO) {
		if (!OpenPlatformTypeEnum.WX_MA.getCode().equals(userLoginReqDTO.getPlatformType())) {
			throw new ArynBusinessException(403, "该登录入口仅支持微信小程序");
		}
	}

	/**
	 * AppID 形如微信小程序但平台声称 H5/APP 的矛盾请求拒绝；
	 * 不能因为省略 platform-type 头就绕过 WX_MA 分支。
	 */
	private void assertPlatformMatchesAppId(UserLoginReqDTO userLoginReqDTO) {
		if (!StringUtils.hasText(userLoginReqDTO.getPlatformType())) {
			throw new ArynBusinessException(403, "缺少平台类型");
		}
		String appId = userLoginReqDTO.getAppId();
		boolean wxLikeAppId = StringUtils.hasText(appId) && appId.matches("wx[0-9a-f]{16}");
		boolean wxMaPlatform = OpenPlatformTypeEnum.WX_MA.getCode().equals(userLoginReqDTO.getPlatformType());
		if (wxLikeAppId && !wxMaPlatform) {
			throw new ArynBusinessException(403, "登录平台与小程序 AppID 不匹配");
		}
	}

	/** 返回的 socialUser 必须属于已验证租户，不允许登录流程中途换租户。 */
	private void assertSocialUserTenant(SocialUser socialUser, String verifiedTenantId) {
		if (Objects.isNull(socialUser) || !verifiedTenantId.equals(socialUser.getTenantId())) {
			throw new ArynBusinessException(403, "三方账号与登录租户不一致");
		}
	}

	/** 商城用户（实体/VO）租户与已验证租户一致；租户字段缺失视为可疑并拒绝。 */
	private void assertUserTenant(String actualTenantId, String verifiedTenantId, String label) {
		if (!verifiedTenantId.equals(actualTenantId)) {
			log.warn("{}租户与已验证租户不一致: actual={}, verified={}", label, actualTenantId, verifiedTenantId);
			throw new ArynBusinessException(403, label + "与登录租户不一致");
		}
	}

	private void bindOpenUser(SocialUser socialUser, String userId, UserLoginReqDTO userLoginReqDTO, String verifiedTenantId) {
		SocialUserBindDTO dto = new SocialUserBindDTO();
		dto.setId(socialUser.getId());
		dto.setMallUserId(userId);
		dto.setAppId(userLoginReqDTO.getAppId());
		dto.setExpectedTenantId(verifiedTenantId);
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
