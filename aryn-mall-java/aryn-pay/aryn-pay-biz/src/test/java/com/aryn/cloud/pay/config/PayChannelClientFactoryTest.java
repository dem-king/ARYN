package com.aryn.cloud.pay.config;

import com.aryn.cloud.pay.PayTestFixtures;
import com.aryn.cloud.pay.api.enums.PayConfigMaterialPurposeEnum;
import com.aryn.cloud.pay.service.PayMaterialParser;
import com.aryn.cloud.pay.service.PayResolvedIdentity;
import com.alipay.api.CertAlipayRequest;
import com.github.binarywang.wxpay.config.WxPayConfig;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayChannelClientFactoryTest {

	private final PayMaterialParser parser = new PayMaterialParser();

	private final WxPayConfiguration wxConfiguration = new WxPayConfiguration(parser);

	private final PayAlipayProperties alipayProperties = new PayAlipayProperties();

	private final AliPayConfiguration aliConfiguration = new AliPayConfiguration(alipayProperties);

	@Test
	void wechatSdkUsesDecryptedContentNotServerPaths() {
		PayResolvedIdentity identity = PayTestFixtures.wechatIdentity("rev-1");

		WxPayConfig config = wxConfiguration.buildConfig(identity);

		assertEquals("wx-synthetic-app", config.getAppId());
		assertEquals("synthetic-mch", config.getMchId());
		assertEquals("PUB_KEY_ID_SYNTHETIC", config.getPublicKeyId());
		assertTrue(config.isFullPublicKeyModel());
		assertArrayEquals(identity.material(PayConfigMaterialPurposeEnum.WX_MERCHANT_PRIVATE_KEY),
				config.getPrivateKeyContent());
		assertArrayEquals(identity.material(PayConfigMaterialPurposeEnum.WX_MERCHANT_CERT),
				config.getPrivateCertContent());
		assertArrayEquals(identity.material(PayConfigMaterialPurposeEnum.WX_PUBLIC_KEY),
				config.getPublicKeyContent());
		assertEquals("0123456789abcdef0123456789abcdef", config.getApiV3Key());
		assertNull(config.getPrivateKeyPath());
		assertNull(config.getPrivateCertPath());
		assertNull(config.getPublicKeyPath());
		assertNull(config.getKeyPath());
		assertTrue(config.getCertSerialNo().matches("[0-9A-F]+"));
	}

	@Test
	void alipaySdkUsesCertificateContentMode() throws Exception {
		PayResolvedIdentity identity = PayTestFixtures.alipayIdentity("rev-2");

		CertAlipayRequest request = aliConfiguration.buildRequest(identity);

		assertEquals("ali-synthetic-app", request.getAppId());
		assertEquals("https://openapi.alipay.com/gateway.do", request.getServerUrl());
		assertEquals("RSA2", request.getSignType());
		assertEquals("json", request.getFormat());
		assertEquals("utf-8", request.getCharset());
		assertTrue(request.getPrivateKey().length() > 100);
		assertFalseContainsHeader(request.getPrivateKey());
		assertEquals(PayTestFixtures.text("synthetic_cert_a.pem").trim(), request.getCertContent());
		assertEquals(PayTestFixtures.text("synthetic_cert_a.pem").trim(), request.getAlipayPublicCertContent());
		assertEquals(PayTestFixtures.text("synthetic_cert_a.pem").trim(), request.getRootCertContent());
		assertNull(request.getCertPath());
		assertNull(request.getAlipayPublicCertPath());
		assertNull(request.getRootCertPath());

		// 构造真实 SDK 客户端，证明内容模式可初始化（不发起任何网络请求）
		aliConfiguration.create(identity);
	}

	@Test
	void sandboxUsesWhitelistedGatewayOnly() {
		PayResolvedIdentity identity = PayTestFixtures.alipayIdentity("rev-2");
		PayResolvedIdentity sandbox = PayResolvedIdentity.builder()
			.tenantId(identity.getTenantId())
			.configId(identity.getConfigId())
			.revisionId(identity.getRevisionId())
			.channel(identity.getChannel())
			.terminalType(identity.getTerminalType())
			.appId(identity.getAppId())
			.mchId(identity.getMchId())
			.environment("sandbox")
			.wxPublicKeyId(identity.getWxPublicKeyId())
			.materials(identity.getMaterials())
			.fingerprints(identity.getFingerprints())
			.build();

		assertEquals("https://openapi-sandbox.dl.alipaydev.com/gateway.do",
				aliConfiguration.buildRequest(sandbox).getServerUrl());
	}

	@Test
	void cacheKeyIncludesIdentityAndMaterialVersion() {
		PayChannelClientFactory factory = new PayChannelClientFactory(wxConfiguration, aliConfiguration);
		PayResolvedIdentity first = PayTestFixtures.wechatIdentity("rev-1");
		PayResolvedIdentity rotated = PayTestFixtures.wechatIdentity("rev-2");

		assertSame(factory.wxPayService(first), factory.wxPayService(first));
		assertEquals(1, factory.cachedWxCount());
		factory.wxPayService(rotated);
		assertEquals(2, factory.cachedWxCount());
		factory.clear();
		assertEquals(0, factory.cachedWxCount());
		assertEquals(0, factory.cachedAliCount());
	}

	private void assertFalseContainsHeader(String value) {
		assertTrue(!value.contains("BEGIN"), "支付宝 SDK 私钥参数应为纯 Base64");
		assertTrue(value.getBytes(StandardCharsets.US_ASCII).length > 0);
	}

	private static void assertArrayEquals(byte[] expected, byte[] actual) {
		org.junit.jupiter.api.Assertions.assertArrayEquals(expected, actual);
	}

}
