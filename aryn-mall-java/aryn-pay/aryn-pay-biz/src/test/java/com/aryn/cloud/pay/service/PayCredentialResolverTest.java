package com.aryn.cloud.pay.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.PayTestFixtures;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.api.entity.PayConfigMaterial;
import com.aryn.cloud.pay.api.entity.PayConfigRevision;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import com.aryn.cloud.pay.api.enums.PayConfigMaterialPurposeEnum;
import com.aryn.cloud.pay.config.PayMaterialCipher;
import com.aryn.cloud.pay.mapper.PayConfigMapper;
import com.aryn.cloud.pay.mapper.PayConfigMaterialMapper;
import com.aryn.cloud.pay.mapper.PayConfigRevisionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayCredentialResolverTest {

	private PayConfigMapper configMapper;

	private PayConfigRevisionMapper revisionMapper;

	private PayConfigMaterialMapper materialMapper;

	private PayCredentialResolver resolver;

	private PayConfig config;

	private final List<PayConfigMaterial> allMaterials = new ArrayList<>();

	@BeforeEach
	void setUp() {
		configMapper = mock(PayConfigMapper.class);
		revisionMapper = mock(PayConfigRevisionMapper.class);
		materialMapper = mock(PayConfigMaterialMapper.class);
		PayMaterialCipher cipher = PayTestFixtures.cipher();
		resolver = new PayCredentialResolver(configMapper, revisionMapper, materialMapper, cipher);
		PayTestFixtures.tenant(PayTestFixtures.TENANT);
		config = PayTestFixtures.config("config-1", "1", "0");
		config.setActiveRevisionId("rev-a2");
		when(configMapper.selectByTenantAndId(PayTestFixtures.TENANT, "config-1")).thenReturn(config);
		when(materialMapper.selectByTenantAndIds(eq(PayTestFixtures.TENANT), any()))
			.thenAnswer(invocation -> {
				Collection<String> ids = invocation.getArgument(1);
				return allMaterials.stream().filter(material -> ids.contains(material.getId())).toList();
			});

		registerRevision("rev-a", "mch-a", false);
		registerRevision("rev-a2", "mch-a", true);
		registerRevision("rev-b", "mch-b", false);
	}

	@AfterEach
	void tearDown() {
		PayTestFixtures.clearTenant();
	}

	@Test
	void sameMerchantRotationUsesCurrentActiveCertificate() {
		PayTradeOrder order = order("rev-a");

		PayResolvedIdentity identity = resolver.resolveForOrder(PayTestFixtures.TENANT, order);

		assertEquals("rev-a2", identity.getRevisionId(), "同商户换证后历史订单应使用当前有效材料");
		assertEquals("mch-a", identity.getMchId());
	}

	@Test
	void merchantChangeDoesNotPolluteHistoricalOrderIdentity() {
		config.setActiveRevisionId("rev-b");
		PayTradeOrder order = order("rev-a");

		PayResolvedIdentity identity = resolver.resolveForOrder(PayTestFixtures.TENANT, order);

		assertEquals("rev-a", identity.getRevisionId(), "换商户不能改写历史订单的原商户身份");
		assertEquals("mch-a", identity.getMchId());
	}

	@Test
	void signatureCandidatesPreferCurrentSameIdentityThenOriginal() {
		List<PayResolvedIdentity> candidates = resolver.resolveForSignature(PayTestFixtures.TENANT, "rev-a");

		assertEquals("rev-a2", candidates.get(0).getRevisionId());
		assertEquals("rev-a", candidates.get(1).getRevisionId());
	}

	@Test
	void signatureCandidatesDoNotMixDifferentMerchant() {
		config.setActiveRevisionId("rev-b");

		List<PayResolvedIdentity> candidates = resolver.resolveForSignature(PayTestFixtures.TENANT, "rev-a");

		assertEquals(1, candidates.size());
		assertEquals("rev-a", candidates.get(0).getRevisionId());
	}

	@Test
	void orderWithoutRevisionFailsClosed() {
		PayTradeOrder order = order(null);

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> resolver.resolveForOrder(PayTestFixtures.TENANT, order));
		assertTrue(exception.getMsg().contains("身份版本"));
	}

	@Test
	void disabledConfigCannotStartNewPayment() {
		config.setEnabled("0");

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> resolver.resolveNewPayment("config-1"));
		assertEquals("支付渠道未启用", exception.getMsg());
	}

	@Test
	void disabledConfigStillResolvesHistoricalOrderForNotifyAndRefund() {
		config.setEnabled("0");

		PayResolvedIdentity identity = resolver.resolveForOrder(PayTestFixtures.TENANT, order("rev-a"));

		assertEquals("rev-a2", identity.getRevisionId(), "停用只禁止新发起，历史回调/退款仍可按原身份处理");
	}

	@Test
	void crossTenantRevisionIsRejected() {
		when(revisionMapper.selectByTenantAndId("other-tenant", "rev-a")).thenReturn(null);

		assertThrows(ArynBusinessException.class,
				() -> resolver.resolveForSignature("other-tenant", "rev-a"));
	}

	private void registerRevision(String revisionId, String mchId, boolean alternateCertificate) {
		Map<String, String> refs = new LinkedHashMap<>();
		for (PayConfigMaterialPurposeEnum purpose : PayConfigMaterialPurposeEnum.requiredFor(
				com.aryn.cloud.pay.api.enums.PayChannelEnum.WECHAT)) {
			byte[] content = contentFor(purpose, alternateCertificate);
			String materialId = revisionId + "-" + purpose.getCode();
			allMaterials.add(PayTestFixtures.encryptedMaterial(PayTestFixtures.cipher(), materialId, "config-1",
					purpose, content));
			refs.put(purpose.getCode(), materialId);
		}
		com.alibaba.fastjson2.JSONObject json = new com.alibaba.fastjson2.JSONObject();
		refs.forEach(json::put);
		PayConfigRevision revision = PayTestFixtures.revision(revisionId, "config-1", "1", "0", "wx-app", mchId,
				json.toJSONString());
		when(revisionMapper.selectByTenantAndId(PayTestFixtures.TENANT, revisionId)).thenReturn(revision);
	}

	private byte[] contentFor(PayConfigMaterialPurposeEnum purpose, boolean alternate) {
		return switch (purpose) {
			case WX_API_V3_KEY -> PayTestFixtures.resource("synthetic_apiv3.key");
			case WX_MERCHANT_PRIVATE_KEY -> PayTestFixtures.resource(
					alternate ? "synthetic_key_b.pem" : "synthetic_key_a.pem");
			case WX_MERCHANT_CERT -> PayTestFixtures.resource(
					alternate ? "synthetic_cert_b.pem" : "synthetic_cert_a.pem");
			case WX_PUBLIC_KEY -> PayTestFixtures.resource("synthetic_public_a.pem");
			default -> throw new IllegalArgumentException("unexpected purpose " + purpose);
		};
	}

	private PayTradeOrder order(String revisionId) {
		return new PayTradeOrder().setId("pay-1")
			.setTenantId(PayTestFixtures.TENANT)
			.setOutTradeNo("ORDER-1")
			.setTerminalType("0")
			.setTradeType("WX_JSAPI_PAY")
			.setPayStatus("0")
			.setAmount(new java.math.BigDecimal("99.90"))
			.setConfigRevisionId(revisionId);
	}

}
