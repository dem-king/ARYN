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
import com.aryn.cloud.user.api.entity.SocialAccount;
import com.aryn.cloud.user.api.entity.SocialUser;
import com.aryn.cloud.user.api.remote.RemoteSocialUserService;
import com.aryn.cloud.user.config.WxMiniAppConfiguration;
import com.aryn.cloud.user.mapper.SocialUserMapper;
import com.aryn.cloud.user.service.ISocialAccountService;
import com.aryn.cloud.user.service.ISocialUserService;
import com.aryn.cloud.user.service.MiniAppBindingResolver;
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

	private final ISocialUserService socialUserService;

	private final SocialUserMapper socialUserMapper;

	private final MiniAppBindingResolver miniAppBindingResolver;

	@Override
	public SocialUser socialLogin(UserLoginReqDTO userLoginReqDTO) {
		// 微信消费前权威重查 AppID↔租户绑定并刷新本实例缓存；消费该次解析得到的配置快照，
		// 不信任进程内旧缓存（可能已被其它实例/直接 SQL 修改淘汰）。
		SocialAccount socialAccount = miniAppBindingResolver.requireBinding(
				userLoginReqDTO.getAppId(), userLoginReqDTO.getRequestTenantId());
		ArynTenantContextHolder.setTenantId(socialAccount.getTenantId());
		final WxMaService wxService = WxMiniAppConfiguration.createMaService(socialAccount);
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
		// 绑定双方必须同属已验证租户：反查回来的记录若与期望租户不一致直接拒绝，
		// 不允许借主键反查把三方账号绑到其它租户的用户上。
		if (StringUtils.hasText(dto.getExpectedTenantId())
				&& !dto.getExpectedTenantId().equals(socialUser.getTenantId())) {
			throw new ArynBusinessException(403, "三方账号与用户租户不一致");
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
		// 手机号解密同样走权威快照：先验证 AppID↔租户绑定，再构造 SDK
		SocialAccount socialAccount = miniAppBindingResolver.requireBinding(
				request.getAppId(), request.getRequestTenantId());
		final WxMaService wxService = WxMiniAppConfiguration.createMaService(socialAccount);
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
