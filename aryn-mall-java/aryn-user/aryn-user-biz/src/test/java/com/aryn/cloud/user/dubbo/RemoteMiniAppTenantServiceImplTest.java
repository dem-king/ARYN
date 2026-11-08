package com.aryn.cloud.user.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.user.api.dto.MiniAppBuyerIdentityVerifyDTO;
import com.aryn.cloud.user.api.dto.MiniAppTenantBindingDTO;
import com.aryn.cloud.user.api.entity.SocialAccount;
import com.aryn.cloud.user.service.ISocialUserService;
import com.aryn.cloud.user.service.IUserInfoService;
import com.aryn.cloud.user.service.MiniAppBindingResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteMiniAppTenantServiceImplTest {

	private static final String TENANT = "tenant-1";

	@Mock
	private MiniAppBindingResolver miniAppBindingResolver;

	@Mock
	private ISocialUserService socialUserService;

	@Mock
	private IUserInfoService userInfoService;

	@AfterEach
	void clearTenant() {
		ArynTenantContextHolder.removeTenantId();
	}

	private RemoteMiniAppTenantServiceImpl service() {
		return new RemoteMiniAppTenantServiceImpl(miniAppBindingResolver, socialUserService, userInfoService);
	}

	private MiniAppBuyerIdentityVerifyDTO request() {
		MiniAppBuyerIdentityVerifyDTO request = new MiniAppBuyerIdentityVerifyDTO();
		request.setTenantId(TENANT);
		request.setPlatformType("ALI_MINI");
		request.setAppId("2021000000000001");
		request.setIdentityMode("user_id");
		request.setBuyerIdentity("2088123456789012");
		request.setMallUserId("mall-user-1");
		return request;
	}

	@Test
	void requireBindingExposesPlatformAndIdentityModeOnly() {
		SocialAccount account = new SocialAccount().setId("sa-1").setType("ALI_MINI")
			.setAppId("2021000000000001").setIdentityMode("open_id").setTenantId(TENANT);
		when(miniAppBindingResolver.requireBinding("2021000000000001", TENANT, "ALI_MINI")).thenReturn(account);

		MiniAppTenantBindingDTO binding = service().requireBinding("2021000000000001", TENANT, "ALI_MINI");

		assertThat(binding.getPlatformType()).isEqualTo("ALI_MINI");
		assertThat(binding.getIdentityMode()).isEqualTo("open_id");
		assertThat(binding.getReady()).isTrue();
	}

	@Test
	void verifyBuyerIdentityRequiresBindingAndMallUserInSameTenant() {
		ArynTenantContextHolder.setTenantId("previous-tenant");
		when(socialUserService.count(any())).thenReturn(1L);
		when(userInfoService.count(any())).thenReturn(1L);

		assertThat(service().verifyBuyerIdentity(request())).isTrue();
		assertThat(ArynTenantContextHolder.getTenantId()).isEqualTo("previous-tenant");
	}

	@Test
	void verifyBuyerIdentityRejectsUnknownBinding() {
		when(socialUserService.count(any())).thenReturn(0L);

		assertThat(service().verifyBuyerIdentity(request())).isFalse();
		verify(userInfoService, never()).count(any());
	}

	private MiniAppBuyerIdentityVerifyDTO wxRequest() {
		MiniAppBuyerIdentityVerifyDTO request = request();
		request.setPlatformType("WX_MA");
		request.setAppId("wx0a8242ea59f3e6b4");
		request.setIdentityMode("open_id");
		request.setBuyerIdentity("ozexS3bXeBjIkrfIZoe07SWXBY4I");
		return request;
	}

	@Test
	void verifyWechatBuyerIdentityRequiresWxBindingAndMallUser() {
		when(socialUserService.count(any())).thenReturn(1L);
		when(userInfoService.count(any())).thenReturn(1L);

		assertThat(service().verifyBuyerIdentity(wxRequest())).isTrue();
	}

	@Test
	void verifyWechatBuyerIdentityRejectsAlipayModeWithoutQuery() {
		MiniAppBuyerIdentityVerifyDTO request = wxRequest();
		request.setIdentityMode("user_id");

		assertThat(service().verifyBuyerIdentity(request)).isFalse();
		verify(socialUserService, never()).count(any());
	}

	@Test
	void verifyBuyerIdentityRejectsUnknownPlatformWithoutQuery() {
		MiniAppBuyerIdentityVerifyDTO request = request();
		request.setPlatformType("H5");

		assertThat(service().verifyBuyerIdentity(request)).isFalse();
		verify(socialUserService, never()).count(any());
	}

	@Test
	void verifyBuyerIdentityRejectsMissingIdentityMode() {
		MiniAppBuyerIdentityVerifyDTO request = request();
		request.setIdentityMode(null);

		assertThat(service().verifyBuyerIdentity(request)).isFalse();
		verify(socialUserService, never()).count(any());
	}

}
