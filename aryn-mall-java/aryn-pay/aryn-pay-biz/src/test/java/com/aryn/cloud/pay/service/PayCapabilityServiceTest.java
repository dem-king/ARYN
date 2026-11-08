package com.aryn.cloud.pay.service;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.PayTestFixtures;
import com.aryn.cloud.pay.api.vo.PayCapabilityVO;
import com.aryn.cloud.pay.api.vo.PayConfigVO;
import com.aryn.cloud.pay.mapper.PayConfigMapper;
import com.aryn.cloud.user.api.dto.MiniAppTenantBindingDTO;
import com.aryn.cloud.user.api.remote.RemoteMiniAppTenantService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayCapabilityServiceTest {

	private final PayConfigMapper configMapper = mock(PayConfigMapper.class);

	private final PayCredentialResolver resolver = mock(PayCredentialResolver.class);

	private final RemoteMiniAppTenantService remoteMiniAppTenantService = mock(RemoteMiniAppTenantService.class);

	private final PayCapabilityService service = new PayCapabilityService(configMapper, resolver);

	{
		ReflectionTestUtils.setField(service, "remoteMiniAppTenantService", remoteMiniAppTenantService);
	}

	@AfterEach
	void clearTenant() {
		ArynTenantContextHolder.removeTenantId();
	}

	private MiniAppTenantBindingDTO binding(String mode) {
		MiniAppTenantBindingDTO binding = new MiniAppTenantBindingDTO();
		binding.setAppId("2021000000000001");
		binding.setTenantId(PayTestFixtures.TENANT);
		binding.setPlatformType("ALI_MINI");
		binding.setIdentityMode(mode);
		binding.setReady(Boolean.TRUE);
		return binding;
	}

	@Test
	void miniProgramCapabilitiesArePlatformIsolated() {
		List<PayCapabilityVO> wechatMini = service.query("0", "WX_MA");
		assertTrue(wechatMini.stream().anyMatch(item -> "WX_JSAPI_PAY".equals(item.getTradeType())));
		assertFalse(wechatMini.stream().anyMatch(item -> "ALI_JSAPI_PAY".equals(item.getTradeType())));

		List<PayCapabilityVO> alipayMini = service.query("0", "ALI_MINI");
		assertTrue(alipayMini.stream().anyMatch(item -> "ALI_JSAPI_PAY".equals(item.getTradeType())));
		assertFalse(alipayMini.stream().anyMatch(item -> "WX_JSAPI_PAY".equals(item.getTradeType())));

		List<PayCapabilityVO> wechatMiniAlias = service.query("0", "WX_MINI");
		assertTrue(wechatMiniAlias.stream().anyMatch(item -> "WX_JSAPI_PAY".equals(item.getTradeType())));
		assertFalse(wechatMiniAlias.stream().anyMatch(item -> "ALI_JSAPI_PAY".equals(item.getTradeType())));
	}

	@Test
	void alipayMiniAvailableOnlyWhenBuyerModeAndSocialBindingMatch() {
		ArynTenantContextHolder.setTenantId(PayTestFixtures.TENANT);
		PayConfigVO config = enabledConfig("ali-config");
		when(configMapper.selectConfig("2", "0")).thenReturn(config);
		when(resolver.resolveNewPayment("ali-config"))
			.thenReturn(PayTestFixtures.alipayMiniIdentity("rev-3", "user_id"));
		when(remoteMiniAppTenantService.requireBinding("2021000000000001", PayTestFixtures.TENANT, "ALI_MINI"))
			.thenReturn(binding("user_id"));

		assertTrue(find(service.query("0"), "ALI_JSAPI_PAY").isAvailable());
	}

	@Test
	void alipayMiniNotAvailableWhenSocialAccountModeDiffers() {
		ArynTenantContextHolder.setTenantId(PayTestFixtures.TENANT);
		PayConfigVO config = enabledConfig("ali-config");
		when(configMapper.selectConfig("2", "0")).thenReturn(config);
		when(resolver.resolveNewPayment("ali-config"))
			.thenReturn(PayTestFixtures.alipayMiniIdentity("rev-3", "user_id"));
		when(remoteMiniAppTenantService.requireBinding("2021000000000001", PayTestFixtures.TENANT, "ALI_MINI"))
			.thenReturn(binding("open_id"));

		PayCapabilityVO capability = find(service.query("0"), "ALI_JSAPI_PAY");
		assertFalse(capability.isAvailable());
		assertTrue(capability.getReason().contains("身份模式不一致"));
	}

	@Test
	void alipayMiniNotAvailableWhenBuyerModeMissing() {
		ArynTenantContextHolder.setTenantId(PayTestFixtures.TENANT);
		PayConfigVO config = enabledConfig("ali-config");
		when(configMapper.selectConfig("2", "0")).thenReturn(config);
		when(resolver.resolveNewPayment("ali-config"))
			.thenReturn(PayTestFixtures.alipayMiniIdentity("rev-3", null));

		PayCapabilityVO capability = find(service.query("0"), "ALI_JSAPI_PAY");
		assertFalse(capability.isAvailable());
		assertTrue(capability.getReason().contains("身份模式未配置"));
	}

	@Test
	void reservedScenariosAreNotAdvertisedAsAvailable() {
		PayConfigVO config = enabledConfig("config-1");
		when(configMapper.selectConfig(anyString(), anyString())).thenReturn(config);
		when(resolver.resolveNewPayment("config-1")).thenReturn(PayTestFixtures.wechatIdentity("rev-1"));

		List<PayCapabilityVO> capabilities = service.query(null);

		assertTrue(capabilities.stream().anyMatch(capability -> "WX_JSAPI_PAY".equals(capability.getTradeType())),
				capabilities.toString());
		PayCapabilityVO wxMini = find(capabilities, "WX_JSAPI_PAY");
		assertTrue(wxMini.isAvailable());
		assertTrue(find(capabilities, "WX_H5_PAY").isAvailable());
		assertFalse(find(capabilities, "ALI_JSAPI_PAY").isAvailable());
	}

	@Test
	void disabledChannelIsNotAvailableAndCarriesReason() {
		PayConfigVO config = enabledConfig("config-1");
		config.setEnabled("0");
		when(configMapper.selectConfig(anyString(), anyString())).thenReturn(config);

		PayCapabilityVO capability = find(service.query("0"), "WX_JSAPI_PAY");

		assertFalse(capability.isAvailable());
		assertTrue(capability.getReason().contains("未启用"));
	}

	@Test
	void brokenMaterialIsNotAvailable() {
		PayConfigVO config = enabledConfig("config-1");
		when(configMapper.selectConfig(anyString(), anyString())).thenReturn(config);
		when(resolver.resolveNewPayment("config-1")).thenThrow(new ArynBusinessException("支付渠道材料不完整"));

		PayCapabilityVO capability = find(service.query("0"), "WX_JSAPI_PAY");

		assertFalse(capability.isAvailable());
		assertTrue(capability.getReason().contains("材料不完整"));
	}

	private PayConfigVO enabledConfig(String id) {
		PayConfigVO config = new PayConfigVO();
		config.setId(id);
		config.setEnabled("1");
		config.setActiveRevisionId("rev-1");
		return config;
	}

	private PayCapabilityVO find(List<PayCapabilityVO> capabilities, String tradeType) {
		return capabilities.stream()
			.filter(capability -> tradeType.equals(capability.getTradeType()))
			.findFirst()
			.orElseThrow();
	}

}
