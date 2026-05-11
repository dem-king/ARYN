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
import com.aryn.cloud.user.service.ISocialAccountService;
import com.aryn.cloud.user.service.ISocialUserService;
import lombok.RequiredArgsConstructor;
import me.chanjar.weixin.common.error.WxErrorException;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
@DubboService
@RequiredArgsConstructor
public class RemoteSocialUserServiceImpl implements RemoteSocialUserService {

	private final ISocialAccountService socialAccountService;

	private final ISocialUserService socialUserService;

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
			throw new ArynBusinessException("用户不存在");
		}
		socialUser.setMallUserId(dto.getMallUserId());
		return socialUserService.updateById(socialUser);
	}

	@Override
	public boolean unbindUserId(SocialUserUnbindDTO dto) {
		SocialUser wxUser = socialUserService.getOne(Wrappers.<SocialUser>lambdaQuery()
			.eq(SocialUser::getAppId, dto.getAppId())
			.eq(SocialUser::getOpenId, dto.getOpenId())
			.eq(SocialUser::getMallUserId, dto.getMallUserId()));
		if (Objects.nonNull(wxUser)) {
			wxUser.setMallUserId("");
			return socialUserService.updateById(wxUser);
		}
		return true;
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
