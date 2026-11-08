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

	private static final String WX_APP_ID = "wx0a8242ea59f3e6b4";

	private static final String ALI_APP_ID = "2021000000000001";

	@Mock
	private SocialAccountMapper socialAccountMapper;

	private final WxMiniAppConfigCache configCache = new WxMiniAppConfigCache();

	private MiniAppBindingResolver resolver;

	@BeforeEach
	void setUp() {
		resolver = new MiniAppBindingResolver(socialAccountMapper, configCache);
	}

	private SocialAccount wxAccount(String tenantId) {
		return new SocialAccount()
			.setId("sa-wx")
			.setType("WX_MA")
			.setAppId(WX_APP_ID)
			.setAppSecret("secret")
			.setTenantId(tenantId);
	}

	private SocialAccount aliAccount(String tenantId, String identityMode) {
		return new SocialAccount()
			.setId("sa-ali")
			.setType("ALI_MINI")
			.setAppId(ALI_APP_ID)
			.setAppSecret("")
			.setIdentityMode(identityMode)
			.setTenantId(tenantId);
	}

	@Test
	void requireBindingAcceptsExactTenantMatchAndRefreshesCache() {
		when(socialAccountMapper.selectValidByAppIdAndType(WX_APP_ID, "WX_MA"))
			.thenReturn(List.of(wxAccount("tenant-1")));

		SocialAccount resolved = resolver.requireBinding(WX_APP_ID, "tenant-1");

		assertThat(resolved.getTenantId()).isEqualTo("tenant-1");
		assertThat(configCache.getByAppId(WX_APP_ID)).isNotNull();
	}

	@Test
	void requireBindingRejectsTenantMismatchWithoutAutoCorrecting() {
		when(socialAccountMapper.selectValidByAppIdAndType(WX_APP_ID, "WX_MA"))
			.thenReturn(List.of(wxAccount("tenant-1")));

		assertThatThrownBy(() -> resolver.requireBinding(WX_APP_ID, "tenant-2"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("小程序与租户配置不一致");
		assertThat(configCache.getByAppId(WX_APP_ID)).isNull();
	}

	@Test
	void requireBindingRejectsUnknownAppId() {
		when(socialAccountMapper.selectValidByAppIdAndType("wxunknownunknown00", "WX_MA")).thenReturn(List.of());

		assertThatThrownBy(() -> resolver.requireBinding("wxunknownunknown00", "tenant-1"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("小程序与租户配置不一致");
	}

	@Test
	void requireBindingFailsClosedOnDuplicateAppId() {
		when(socialAccountMapper.selectValidByAppIdAndType(WX_APP_ID, "WX_MA"))
			.thenReturn(List.of(wxAccount("tenant-1"), wxAccount("tenant-2")));

		assertThatThrownBy(() -> resolver.requireBinding(WX_APP_ID, "tenant-1"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("小程序与租户配置不一致");
	}

	@Test
	void requireBindingRejectsMissingHeaderValuesWithoutQuerying() {
		assertThatThrownBy(() -> resolver.requireBinding(null, "tenant-1"))
			.isInstanceOf(ArynBusinessException.class);
		assertThatThrownBy(() -> resolver.requireBinding(WX_APP_ID, ""))
			.isInstanceOf(ArynBusinessException.class);
		verify(socialAccountMapper, never()).selectValidByAppIdAndType(anyString(), anyString());
	}

	@Test
	void requireBindingRejectsIncompleteSecret() {
		SocialAccount incomplete = wxAccount("tenant-1").setAppSecret("");
		when(socialAccountMapper.selectValidByAppIdAndType(WX_APP_ID, "WX_MA")).thenReturn(List.of(incomplete));

		assertThatThrownBy(() -> resolver.requireBinding(WX_APP_ID, "tenant-1"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("小程序配置不完整");
	}

	@Test
	void requireBindingAcceptsAlipayMiniWithoutSecretAndDoesNotPolluteWxCache() {
		when(socialAccountMapper.selectValidByAppIdAndType(ALI_APP_ID, "ALI_MINI"))
			.thenReturn(List.of(aliAccount("tenant-1", "user_id")));

		SocialAccount resolved = resolver.requireBinding(ALI_APP_ID, "tenant-1", "ALI_MINI");

		assertThat(resolved.getIdentityMode()).isEqualTo("user_id");
		assertThat(configCache.getByAppId(ALI_APP_ID)).isNull();
	}

	@Test
	void requireBindingRejectsAlipayMiniWithoutIdentityMode() {
		when(socialAccountMapper.selectValidByAppIdAndType(ALI_APP_ID, "ALI_MINI"))
			.thenReturn(List.of(aliAccount("tenant-1", null)));

		assertThatThrownBy(() -> resolver.requireBinding(ALI_APP_ID, "tenant-1", "ALI_MINI"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("支付宝小程序身份模式未配置");
	}

	@Test
	void requireBindingRejectsWechatAccountForAlipayPlatform() {
		// 同一 AppID 只有 WX_MA 记录时，ALI_MINI 查询必须 fail-closed，不能跨类型复用
		when(socialAccountMapper.selectValidByAppIdAndType(WX_APP_ID, "ALI_MINI")).thenReturn(List.of());

		assertThatThrownBy(() -> resolver.requireBinding(WX_APP_ID, "tenant-1", "ALI_MINI"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("小程序与租户配置不一致");
	}

	@Test
	void requireBindingRejectsUnknownPlatform() {
		assertThatThrownBy(() -> resolver.requireBinding(WX_APP_ID, "tenant-1", "H5"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("不支持的小程序平台");
	}

}
