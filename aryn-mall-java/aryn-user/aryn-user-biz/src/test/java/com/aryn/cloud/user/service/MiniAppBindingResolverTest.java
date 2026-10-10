package com.aryn.cloud.user.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.entity.SocialAccount;
import com.aryn.cloud.user.config.WxMiniAppConfigCache;
import com.aryn.cloud.user.mapper.SocialAccountMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MiniAppBindingResolverTest {

	@Mock
	private SocialAccountMapper socialAccountMapper;

	private final WxMiniAppConfigCache configCache = new WxMiniAppConfigCache();

	private MiniAppBindingResolver resolver;

	@BeforeEach
	void setUp() {
		resolver = new MiniAppBindingResolver(socialAccountMapper, configCache);
	}

	private SocialAccount account(String tenantId) {
		return new SocialAccount()
				.setId("sa-1")
				.setType("WX_MA")
				.setAppId("wx0a8242ea59f3e6b4")
				.setAppSecret("secret")
				.setTenantId(tenantId);
	}

	@Test
	void requireBindingAcceptsExactTenantMatchAndRefreshesCache() {
		when(socialAccountMapper.selectValidWxMaByAppId("wx0a8242ea59f3e6b4"))
			.thenReturn(List.of(account("tenant-1")));

		SocialAccount resolved = resolver.requireBinding("wx0a8242ea59f3e6b4", "tenant-1");

		assertThat(resolved.getTenantId()).isEqualTo("tenant-1");
		// 权威记录刷新进本实例缓存（消费同源）
		assertThat(configCache.getByAppId("wx0a8242ea59f3e6b4")).isNotNull();
	}

	@Test
	void requireBindingRejectsTenantMismatchWithoutAutoCorrecting() {
		when(socialAccountMapper.selectValidWxMaByAppId("wx0a8242ea59f3e6b4"))
			.thenReturn(List.of(account("tenant-1")));

		assertThatThrownBy(() -> resolver.requireBinding("wx0a8242ea59f3e6b4", "tenant-2"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("小程序与租户配置不一致");
		// 拒绝时不污染缓存
		assertThat(configCache.getByAppId("wx0a8242ea59f3e6b4")).isNull();
	}

	@Test
	void requireBindingRejectsUnknownAppId() {
		when(socialAccountMapper.selectValidWxMaByAppId("wxunknownunknown00")).thenReturn(List.of());

		assertThatThrownBy(() -> resolver.requireBinding("wxunknownunknown00", "tenant-1"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("小程序与租户配置不一致");
	}

	@Test
	void requireBindingFailsClosedOnDuplicateAppId() {
		when(socialAccountMapper.selectValidWxMaByAppId("wx0a8242ea59f3e6b4"))
			.thenReturn(List.of(account("tenant-1"), account("tenant-2")));

		assertThatThrownBy(() -> resolver.requireBinding("wx0a8242ea59f3e6b4", "tenant-1"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("小程序与租户配置不一致");
	}

	@Test
	void requireBindingRejectsMissingHeaderValuesWithoutQuerying() {
		assertThatThrownBy(() -> resolver.requireBinding(null, "tenant-1"))
			.isInstanceOf(ArynBusinessException.class);
		assertThatThrownBy(() -> resolver.requireBinding("wx0a8242ea59f3e6b4", ""))
			.isInstanceOf(ArynBusinessException.class);
		verify(socialAccountMapper, never()).selectValidWxMaByAppId(anyString());
	}

	@Test
	void requireBindingRejectsIncompleteSecret() {
		SocialAccount incomplete = account("tenant-1").setAppSecret("");
		when(socialAccountMapper.selectValidWxMaByAppId("wx0a8242ea59f3e6b4")).thenReturn(List.of(incomplete));

		assertThatThrownBy(() -> resolver.requireBinding("wx0a8242ea59f3e6b4", "tenant-1"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("小程序配置不完整");
	}

}
