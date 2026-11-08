package com.aryn.cloud.pay.service;

import com.aryn.cloud.pay.PayTestFixtures;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.api.entity.PayConfigMaterial;
import com.aryn.cloud.pay.api.entity.PayConfigRevision;
import com.aryn.cloud.pay.api.enums.PayConfigMaterialPurposeEnum;
import com.aryn.cloud.pay.api.vo.PayConfigCheckResultVO;
import com.aryn.cloud.pay.config.PayCredentialProperties;
import com.aryn.cloud.pay.config.PayMaterialCipher;
import com.aryn.cloud.pay.mapper.PayConfigMapper;
import com.aryn.cloud.pay.mapper.PayConfigMaterialMapper;
import com.aryn.cloud.pay.mapper.PayConfigRevisionMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PayConfigCheckServiceTest {

	private PayConfigMapper configMapper;

	private PayConfigRevisionMapper revisionMapper;

	private PayConfigMaterialMapper materialMapper;

	private PayMaterialParser parser;

	private PayMaterialCipher cipher;

	private PayConfigCheckService service;

	private PayConfig config;

	private PayConfigRevision revision;

	private final Map<String, PayConfigMaterial> materials = new LinkedHashMap<>();

	@BeforeEach
	void setUp() {
		configMapper = mock(PayConfigMapper.class);
		revisionMapper = mock(PayConfigRevisionMapper.class);
		materialMapper = mock(PayConfigMaterialMapper.class);
		parser = new PayMaterialParser();
		cipher = PayTestFixtures.cipher();
		PayCredentialResolver resolver = new PayCredentialResolver(configMapper, revisionMapper, materialMapper, cipher);
		service = new PayConfigCheckService(configMapper, revisionMapper, materialMapper, parser, cipher, resolver);
		PayTestFixtures.tenant(PayTestFixtures.TENANT);
		config = PayTestFixtures.config("config-1", "1", "0");
		config.setActiveRevisionId("rev-1");
		when(configMapper.selectByTenantAndId(PayTestFixtures.TENANT, "config-1")).thenReturn(config);
		when(materialMapper.selectById(anyString())).thenAnswer(invocation -> materials.get(invocation.getArgument(0)));
		when(materialMapper.selectByTenantAndIds(eq(PayTestFixtures.TENANT), any()))
			.thenAnswer(invocation -> new ArrayList<>(materials.values()));
	}

	@AfterEach
	void tearDown() {
		PayTestFixtures.clearTenant();
	}

	@Test
	void completeMaterialsPassLocalCheckOnly() {
		registerMaterials("synthetic_key_a.pem", "synthetic_cert_a.pem", "synthetic_public_a.pem");

		PayConfigCheckResultVO result = service.check("config-1", "rev-1");

		assertTrue(result.isPassed());
		assertEquals(PayConfigCheckResultVO.PROOF_LOCAL_ONLY, result.getProofLevel());
		assertTrue(result.getItems().stream().anyMatch(item -> "PAIR_WX_MERCHANT_PRIVATE_KEY".equals(item.getCode())));
	}

	@Test
	void mismatchedPrivateKeyFailsCheck() {
		registerMaterials("synthetic_key_b.pem", "synthetic_cert_a.pem", "synthetic_public_a.pem");

		PayConfigCheckResultVO result = service.check("config-1", "rev-1");

		assertFalse(result.isPassed());
		assertTrue(result.getItems()
			.stream()
			.anyMatch(item -> "PAIR_WX_MERCHANT_PRIVATE_KEY".equals(item.getCode()) && !item.isPassed()));
	}

	@Test
	void expiredCertificateFailsCheck() {
		registerMaterials("synthetic_key_a.pem", "synthetic_cert_expired.pem", "synthetic_public_a.pem");

		PayConfigCheckResultVO result = service.check("config-1", "rev-1");

		assertFalse(result.isPassed());
		assertTrue(result.getItems()
			.stream()
			.anyMatch(item -> "MATERIAL_WX_MERCHANT_CERT".equals(item.getCode()) && !item.isPassed()));
	}

	@Test
	void missingMasterKeyFailsBeforeAnyMaterialIsUsed() {
		PayCredentialProperties unconfiguredProperties = new PayCredentialProperties();
		PayMaterialCipher unconfigured = new PayMaterialCipher(unconfiguredProperties);
		PayConfigCheckService unconfiguredService = new PayConfigCheckService(configMapper, revisionMapper,
				materialMapper, parser, unconfigured,
				new PayCredentialResolver(configMapper, revisionMapper, materialMapper, unconfigured));
		registerMaterials("synthetic_key_a.pem", "synthetic_cert_a.pem", "synthetic_public_a.pem");

		PayConfigCheckResultVO result = unconfiguredService.check("config-1", "rev-1");

		assertFalse(result.isPassed());
		assertTrue(result.getItems()
			.stream()
			.anyMatch(item -> "ENCRYPTION_KEY".equals(item.getCode()) && !item.isPassed()));
	}

	private void registerMaterials(String key, String cert, String publicKey) {
		materials.clear();
		Map<String, String> refs = new LinkedHashMap<>();
		put(PayConfigMaterialPurposeEnum.WX_API_V3_KEY, PayTestFixtures.resource("synthetic_apiv3.key"), refs);
		put(PayConfigMaterialPurposeEnum.WX_MERCHANT_PRIVATE_KEY, PayTestFixtures.resource(key), refs);
		put(PayConfigMaterialPurposeEnum.WX_MERCHANT_CERT, PayTestFixtures.resource(cert), refs);
		put(PayConfigMaterialPurposeEnum.WX_PUBLIC_KEY, PayTestFixtures.resource(publicKey), refs);
		com.alibaba.fastjson2.JSONObject json = new com.alibaba.fastjson2.JSONObject();
		refs.forEach(json::put);
		revision = PayTestFixtures.revision("rev-1", "config-1", "1", "0", "wx-app", "mch-a", json.toJSONString());
		when(revisionMapper.selectByTenantAndId(PayTestFixtures.TENANT, "rev-1")).thenReturn(revision);
	}

	private void put(PayConfigMaterialPurposeEnum purpose, byte[] content, Map<String, String> refs) {
		String id = "mat-" + purpose.getCode();
		materials.put(id, PayTestFixtures.encryptedMaterial(cipher, id, "config-1", purpose, content));
		refs.put(purpose.getCode(), id);
	}

}
