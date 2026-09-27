package com.aryn.cloud.user.dubbo;

import cn.binarywang.wx.miniapp.api.WxMaService;
import cn.binarywang.wx.miniapp.bean.WxMaJscode2SessionResult;
import cn.binarywang.wx.miniapp.bean.WxMaPhoneNumberInfo;
import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.dto.SocialUserBindDTO;
import com.aryn.cloud.user.api.dto.SocialUserUnbindDTO;
import com.aryn.cloud.user.api.dto.UserLoginReqDTO;
import com.aryn.cloud.user.api.entity.SocialUser;
import com.aryn.cloud.user.api.remote.RemoteSocialUserService;
import com.aryn.cloud.user.api.vo.SocialAccountVO;
import com.aryn.cloud.user.config.WxMiniAppConfiguration;
import com.aryn.cloud.user.mapper.SocialUserMapper;
import com.aryn.cloud.user.service.ISocialAccountService;
import com.aryn.cloud.user.service.ISocialUserService;
import lombok.RequiredArgsConstructor;
import me.chanjar.weixin.common.error.WxErrorException;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
@DubboService
@RequiredArgsConstructor
public class RemoteSocialUserServiceImpl implements RemoteSocialUserService {

	private final ISocialAccountService socialAccountService;

	private final ISocialUserService socialUserService;

	private final SocialUserMapper socialUserMapper;

	@Override
	public SocialUser socialLogin(UserLoginReqDTO userLoginReqDTO) {
		SocialAccountVO socialAccount = socialAccountService.selectByAppId(userLoginReqDTO.getAppId());
		if (Objects.isNull(socialAccount)) {
			throw new ArynBusinessException("三方账号配置不存在");
		}
		ArynTenantContextHolder.setTenantId(socialAccount.getTenantId());
		final WxMaService wxService = WxMiniAppConfiguration.getMaService(socialAccount.getAppId());
		try {
			WxMaJscode2SessionResult session = wxService.getUserService().getSessionInfo(userLoginReqDTO.getJsCode());
			// 查询三方用户
			SocialUser socialUser = socialUserService.getOne(Wrappers.<SocialUser>lambdaQuery()
				.eq(SocialUser::getAppId, socialAccount.getAppId())
				.eq(SocialUser::getOpenId, session.getOpenid()));
			if (ObjectUtil.isNull(socialUser)) {
				socialUser = new SocialUser();
				socialUser.setOpenId(session.getOpenid());
			}
			socialUser.setSessionKey(session.getSessionKey());
			socialUser.setUnionid(session.getUnionid());
			socialUser.setAppId(socialAccount.getAppId());
			socialUser.setSocialAccountId(socialAccount.getId());
			// 通过返回值把租户带回认证服务，避免 Boot 模式 Dubbo injvm 清理线程上下文后丢失租户。
			socialUser.setTenantId(socialAccount.getTenantId());
			socialUserService.saveOrUpdate(socialUser);
			return socialUser;
		}
		catch (WxErrorException e) {
			throw new ArynBusinessException(e.getMessage());
		}
	}

	@Override
	public boolean bindUserId(SocialUserBindDTO dto) {
		SocialUser socialUser = socialUserService.getById(dto.getId());
		if (Objects.isNull(socialUser)) {
			// social_user 为多租户表，Dubbo injvm 在服务提供方返回时会清理调用方线程上下文，
			// 此时 getById 会被租户条件过滤成 null，表现为首次微信登录报「用户不存在」。
			// 这里按主键跨租户反查同一记录，恢复其真实租户后再执行更新。
			socialUser = findByPrimaryKeyInAnyTenant(dto.getId());
			if (Objects.isNull(socialUser)) {
				throw new ArynBusinessException("用户不存在");
			}
			restoreTenantContext(socialUser);
		}
		socialUser.setMallUserId(dto.getMallUserId());
		return socialUserService.updateById(socialUser);
	}

	private SocialUser findByPrimaryKeyInAnyTenant(String id) {
		return socialUserMapper.selectByIdInAnyTenant(id);
	}

	private void restoreTenantContext(SocialUser socialUser) {
		if (StringUtils.hasText(socialUser.getTenantId())) {
			ArynTenantContextHolder.setTenantId(socialUser.getTenantId());
		}
	}

	@Override
	public boolean unbindUserId(SocialUserUnbindDTO dto) {
		SocialUser wxUser = socialUserService.getOne(Wrappers.<SocialUser>lambdaQuery()
			.eq(SocialUser::getAppId, dto.getAppId())
			.eq(SocialUser::getOpenId, dto.getOpenId())
			.eq(SocialUser::getMallUserId, dto.getMallUserId()));
		if (Objects.isNull(wxUser)) {
			// 与 bindUserId 同源：租户上下文缺失时带租户条件的查询会查不到记录，
			// 退化成跨租户定位后再恢复真实租户，避免退出登录静默解绑失败。
			// 反查条件与上面保持一致（appId + openId + mallUserId），不能只用 appId：
			// 同一小程序下每个 openId 各有一条记录，单键会取错行。
			wxUser = socialUserMapper.selectByAppIdAndOpenIdInAnyTenant(dto.getAppId(), dto.getOpenId(),
					dto.getMallUserId());
			if (Objects.isNull(wxUser)) {
				return true;
			}
			restoreTenantContext(wxUser);
		}
		wxUser.setMallUserId("");
		return socialUserService.updateById(wxUser);
	}

	@Override
	public String getPhoneNumberInfo(UserLoginReqDTO request) {
		final WxMaService wxService = WxMiniAppConfiguration.getMaService(request.getAppId());
		// 解密
		WxMaPhoneNumberInfo phoneNoInfo = null;
		try {
			phoneNoInfo = wxService.getUserService().getPhoneNoInfo(request.getCode());
		}
		catch (WxErrorException e) {
			throw new RuntimeException(e);
		}
		return phoneNoInfo.getPhoneNumber();
	}

}
