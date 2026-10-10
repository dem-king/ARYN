package com.aryn.cloud.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.user.api.dto.UserLoginReqDTO;
import com.aryn.cloud.user.api.entity.SocialUser;
import com.aryn.cloud.user.api.entity.UserInfo;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.remote.RemoteSocialUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TocLoginServiceTest {

	@Mock
	private RemoteSocialUserService remoteSocialUserService;

	@Mock
	private RemoteMallUserService remoteMallUserService;

	@Mock
	private LoginTenantGuard loginTenantGuard;

	private MockedStatic<SecurityUtils> securityUtils;

	private MockedStatic<StpUtil> stpUtil;

	@BeforeEach
	void setUp() {
		securityUtils = mockStatic(SecurityUtils.class);
		stpUtil = mockStatic(StpUtil.class);
		stpUtil.when(StpUtil::getTokenInfo).thenReturn(new cn.dev33.satoken.stp.SaTokenInfo());
	}

	@AfterEach
	void cleanUp() {
		securityUtils.close();
		stpUtil.close();
		ArynTenantContextHolder.removeTenantId();
	}

	private UserLoginReqDTO wxMaRequest(String tenantId) {
		UserLoginReqDTO request = new UserLoginReqDTO();
		request.setAppId("wx0a8242ea59f3e6b4");
		request.setJsCode("js-code");
		request.setPlatformType("WX_MA");
		request.setRequestTenantId(tenantId);
		return request;
	}

	@Test
	void miniProgramLoginUsesVerifiedTenantAndRestoresContext() {
		when(loginTenantGuard.requireVerifiedTenant("wx0a8242ea59f3e6b4", "tenant-1")).thenReturn("tenant-1");
		SocialUser socialUser = new SocialUser().setId("social-1").setOpenId("openid-1").setTenantId("tenant-1");
		when(remoteSocialUserService.socialLogin(any())).thenReturn(socialUser);
		when(remoteSocialUserService.bindUserId(any())).thenReturn(true);
		when(remoteMallUserService.getUserByOpenId("openid-1", "WX_MA"))
			.thenAnswer(invocation -> {
				// guard 已把已验证租户放进 ThreadLocal，用户创建在验证租户内进行
				assertThat(ArynTenantContextHolder.getTenantId()).isEqualTo("tenant-1");
				return new UserInfo().setId("user-1").setTenantId("tenant-1");
			});

		TocLoginService service = new TocLoginService(remoteSocialUserService, remoteMallUserService, loginTenantGuard);
		service.maLogin(wxMaRequest("tenant-1"));

		verify(remoteMallUserService).getUserByOpenId("openid-1", "WX_MA");
		// finally 恢复进入前上下文（无租户）
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void maLoginRejectsWhenSocialUserTenantDiffersFromVerified() {
		when(loginTenantGuard.requireVerifiedTenant("wx0a8242ea59f3e6b4", "tenant-1")).thenReturn("tenant-1");
		SocialUser socialUser = new SocialUser().setId("social-1").setOpenId("openid-1").setTenantId("tenant-other");
		when(remoteSocialUserService.socialLogin(any())).thenReturn(socialUser);

		TocLoginService service = new TocLoginService(remoteSocialUserService, remoteMallUserService, loginTenantGuard);
		assertThatThrownBy(() -> service.maLogin(wxMaRequest("tenant-1")))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("三方账号与登录租户不一致");

		// 错配时无用户副作用、无绑定、无登录
		verifyNoInteractions(remoteMallUserService);
		verify(remoteSocialUserService, never()).bindUserId(any());
		securityUtils.verify(() -> SecurityUtils.loginByDevice(any(), any()), never());
	}

	@Test
	void maPhoneLoginDecryptsPhoneOnlyAfterBindingVerified() {
		when(loginTenantGuard.requireVerifiedTenant("wx0a8242ea59f3e6b4", "tenant-1")).thenReturn("tenant-1");
		SocialUser socialUser = new SocialUser().setId("social-1").setOpenId("openid-1").setTenantId("tenant-1");
		when(remoteSocialUserService.getPhoneNumberInfo(any())).thenReturn("13800000000");
		when(remoteMallUserService.getInfoByPhone("13800000000", "WX_MA"))
			.thenAnswer(invocation -> {
				assertThat(ArynTenantContextHolder.getTenantId()).isEqualTo("tenant-1");
				return new UserInfo().setId("user-1").setTenantId("tenant-1");
			});
		when(remoteSocialUserService.socialLogin(any())).thenReturn(socialUser);
		when(remoteSocialUserService.bindUserId(any())).thenReturn(true);

		TocLoginService service = new TocLoginService(remoteSocialUserService, remoteMallUserService, loginTenantGuard);
		service.maPhoneLogin(wxMaRequest("tenant-1"));

		// guard 先于手机号解密发生：错误绑定（抛异常）时解密不应被调用，这里验证调用顺序
		org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(loginTenantGuard, remoteSocialUserService);
		inOrder.verify(loginTenantGuard).requireVerifiedTenant("wx0a8242ea59f3e6b4", "tenant-1");
		inOrder.verify(remoteSocialUserService).getPhoneNumberInfo(any());
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void smsLoginKeepsH5BranchWithoutGuard() {
		UserLoginReqDTO request = wxMaRequest("tenant-1");
		request.setAppId("h5-client-id");
		request.setPlatformType("H5");
		request.setPhone("13800000000");
		UserInfo userInfo = new UserInfo().setId("user-1").setTenantId("tenant-h5");
		when(remoteMallUserService.getInfoByPhone("13800000000", "H5")).thenReturn(userInfo);

		TocLoginService service = new TocLoginService(remoteSocialUserService, remoteMallUserService, loginTenantGuard);
		service.smsLogin(request);

		// H5 分支保留既有语义：不走 guard、不查三方
		verifyNoInteractions(loginTenantGuard);
		verify(remoteSocialUserService, never()).socialLogin(any());
	}

	@Test
	void smsLoginRejectsWxAppIdWithH5PlatformMismatch() {
		UserLoginReqDTO request = wxMaRequest("tenant-1");
		request.setPlatformType("H5");

		TocLoginService service = new TocLoginService(remoteSocialUserService, remoteMallUserService, loginTenantGuard);
		assertThatThrownBy(() -> service.smsLogin(request))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("登录平台与小程序 AppID 不匹配");
		verifyNoInteractions(remoteMallUserService);
	}

}
