package com.aryn.cloud.user.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.dto.SocialUserBindDTO;
import com.aryn.cloud.user.api.dto.SocialUserUnbindDTO;
import com.aryn.cloud.user.api.entity.SocialUser;
import com.aryn.cloud.user.mapper.SocialUserMapper;
import com.aryn.cloud.user.service.ISocialAccountService;
import com.aryn.cloud.user.service.ISocialUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RemoteSocialUserServiceImplTest {

	@Mock
	private ISocialAccountService socialAccountService;

	@Mock
	private ISocialUserService socialUserService;

	@Mock
	private SocialUserMapper socialUserMapper;

	@AfterEach
	void clearTenant() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void bindUserIdRecoversTenantWhenContextLostByDubboInjvm() {
		// 模拟 Dubbo injvm 返回后租户上下文被清空：按 id 在拦截器下查不到记录
		SocialUser socialUser = new SocialUser().setId("social-1").setAppId("wx-app").setTenantId("tenant-1");
		when(socialUserService.getById("social-1")).thenReturn(null);
		when(socialUserMapper.selectByIdInAnyTenant("social-1")).thenReturn(socialUser);
		when(socialUserService.updateById(socialUser)).thenAnswer(invocation -> {
			assertThat(ArynTenantContextHolder.getTenantId())
				.as("绑定更新必须恢复记录所属租户，否则 updateById 同样会被租户条件过滤")
				.isEqualTo("tenant-1");
			return true;
		});

		SocialUserBindDTO dto = new SocialUserBindDTO();
		dto.setId("social-1");
		dto.setAppId("wx-app");
		dto.setMallUserId("user-1");

		assertThat(service().bindUserId(dto)).isTrue();
		assertThat(socialUser.getMallUserId()).isEqualTo("user-1");
		assertThat(ArynTenantContextHolder.getTenantId()).isEqualTo("tenant-1");
	}

	@Test
	void bindUserIdKeepsHappyPathWhenTenantMatches() {
		SocialUser socialUser = new SocialUser().setId("social-1").setAppId("wx-app").setTenantId("tenant-1");
		when(socialUserService.getById("social-1")).thenReturn(socialUser);
		when(socialUserService.updateById(socialUser)).thenReturn(true);

		SocialUserBindDTO dto = new SocialUserBindDTO();
		dto.setId("social-1");
		dto.setAppId("wx-app");
		dto.setMallUserId("user-1");

		assertThat(service().bindUserId(dto)).isTrue();
		verify(socialUserMapper, never()).selectByIdInAnyTenant(org.mockito.ArgumentMatchers.any());
		verify(socialUserMapper, never()).selectByAppIdAndOpenIdInAnyTenant(org.mockito.ArgumentMatchers.any(),
				org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void bindUserIdStillFailsWhenRecordTrulyAbsent() {
		when(socialUserService.getById("social-1")).thenReturn(null);
		when(socialUserMapper.selectByIdInAnyTenant("social-1")).thenReturn(null);

		SocialUserBindDTO dto = new SocialUserBindDTO();
		dto.setId("social-1");
		dto.setAppId("wx-app");
		dto.setMallUserId("user-1");

		assertThatThrownBy(() -> service().bindUserId(dto))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("用户不存在");
	}

	@Test
	void unbindUserIdRecoversTenantWhenContextLostByDubboInjvm() {
		SocialUser socialUser = new SocialUser().setId("social-1").setAppId("wx-app")
			.setOpenId("openid-1").setMallUserId("user-1").setTenantId("tenant-1");
		when(socialUserService.getOne(org.mockito.ArgumentMatchers.any())).thenReturn(null);
		when(socialUserMapper.selectByAppIdAndOpenIdInAnyTenant("wx-app", "openid-1", "user-1"))
			.thenReturn(socialUser);
		when(socialUserService.updateById(socialUser)).thenAnswer(invocation -> {
			assertThat(ArynTenantContextHolder.getTenantId()).isEqualTo("tenant-1");
			return true;
		});

		SocialUserUnbindDTO dto = new SocialUserUnbindDTO();
		dto.setAppId("wx-app");
		dto.setOpenId("openid-1");
		dto.setMallUserId("user-1");

		assertThat(service().unbindUserId(dto)).isTrue();
		assertThat(socialUser.getMallUserId()).isEmpty();
	}

	@Test
	void unbindUserIdReverseLookupUsesOpenIdAndMallUserIdNotAppIdAlone() {
		// appId 在同一小程序下会命中多条记录（每个 openId 一条），反查必须带上 openId 与 mallUserId，
		// 否则租户恢复后可能把另一条记录当成解绑目标。
		SocialUser socialUser = new SocialUser().setId("social-1").setAppId("wx-app")
			.setOpenId("openid-1").setMallUserId("user-1").setTenantId("tenant-1");
		when(socialUserService.getOne(org.mockito.ArgumentMatchers.any())).thenReturn(null);
		when(socialUserMapper.selectByAppIdAndOpenIdInAnyTenant("wx-app", "openid-1", "user-1"))
			.thenReturn(socialUser);
		when(socialUserService.updateById(socialUser)).thenReturn(true);

		SocialUserUnbindDTO dto = new SocialUserUnbindDTO();
		dto.setAppId("wx-app");
		dto.setOpenId("openid-1");
		dto.setMallUserId("user-1");

		assertThat(service().unbindUserId(dto)).isTrue();
		verify(socialUserMapper).selectByAppIdAndOpenIdInAnyTenant("wx-app", "openid-1", "user-1");
	}

	private RemoteSocialUserServiceImpl service() {
		return new RemoteSocialUserServiceImpl(socialAccountService, socialUserService, socialUserMapper);
	}

}
