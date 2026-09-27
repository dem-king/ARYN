package com.aryn.cloud.auth.service;

import cn.dev33.satoken.stp.StpUtil;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
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
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TocLoginServiceTest {

	@Mock
	private RemoteSocialUserService remoteSocialUserService;

	@Mock
	private RemoteMallUserService remoteMallUserService;

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

	@Test
	void miniProgramLoginCarriesSocialAccountTenantIntoUserCreation() {
		SocialUser socialUser = new SocialUser().setId("social-1").setOpenId("openid-1").setTenantId("tenant-1");
		when(remoteSocialUserService.socialLogin(org.mockito.ArgumentMatchers.any())).thenReturn(socialUser);
		when(remoteSocialUserService.bindUserId(org.mockito.ArgumentMatchers.any())).thenReturn(true);
		when(remoteMallUserService.getUserByOpenId("openid-1", "WX_MA"))
			.thenAnswer(invocation -> {
				assertThat(ArynTenantContextHolder.getTenantId()).isEqualTo("tenant-1");
				return new UserInfo().setId("user-1");
			});

		TocLoginService service = new TocLoginService(remoteSocialUserService, remoteMallUserService);
		UserLoginReqDTO request = new UserLoginReqDTO();
		request.setAppId("wx-app");
		request.setJsCode("js-code");
		request.setPlatformType("WX_MA");
		service.maLogin(request);

		verify(remoteMallUserService).getUserByOpenId("openid-1", "WX_MA");
		assertThat(ArynTenantContextHolder.getTenantId()).isEqualTo("tenant-1");
	}
}
