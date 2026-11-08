package com.aryn.cloud.pay.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.PayTestFixtures;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.api.entity.PayConfigMaterial;
import com.aryn.cloud.pay.api.enums.PayConfigMaterialPurposeEnum;
import com.aryn.cloud.pay.config.PayCredentialProperties;
import com.aryn.cloud.pay.config.PayMaterialCipher;
import com.aryn.cloud.pay.mapper.PayConfigMapper;
import com.aryn.cloud.pay.mapper.PayConfigMaterialMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayCredentialServiceTest {

	private PayConfigMapper configMapper;

	private PayConfigMaterialMapper materialMapper;

	private PayCredentialProperties properties;

	private PayCredentialService service;

	private PayConfigMaterial certMaterial;

	@BeforeEach
	void setUp() {
		configMapper = mock(PayConfigMapper.class);
		materialMapper = mock(PayConfigMaterialMapper.class);
		properties = PayTestFixtures.properties();
		service = new PayCredentialService(configMapper, materialMapper, new PayMaterialParser(),
				new PayMaterialCipher(properties), properties);
		PayTestFixtures.tenant(PayTestFixtures.TENANT);
		when(configMapper.selectByTenantAndId(PayTestFixtures.TENANT, "config-wx")).thenReturn(wechatConfig());
		certMaterial = PayTestFixtures.encryptedMaterial(new PayMaterialCipher(properties), "material-cert",
				"config-wx", PayConfigMaterialPurposeEnum.WX_MERCHANT_CERT,
				PayTestFixtures.resource("synthetic_cert_a.pem"));
	}

	@AfterEach
	void tearDown() {
		PayTestFixtures.clearTenant();
	}

	@Test
	void uploadReturnsOpaqueMaterialIdAndStoresOnlyCiphertext() {
		MockMultipartFile file = new MockMultipartFile("file", "public.pem", "application/x-pem-file",
				PayTestFixtures.resource("synthetic_public_a.pem"));

		var uploaded = service.uploadMaterial("config-wx", "WX_PUBLIC_KEY", file);

		assertNotNull(uploaded.getMaterialId());
		assertFalse(uploaded.getMaterialId().contains("BEGIN"));
		assertEquals("WX_PUBLIC_KEY", uploaded.getPurpose());
		assertFalse(uploaded.getSensitive());
		ArgumentCaptor<PayConfigMaterial> captor = ArgumentCaptor.forClass(PayConfigMaterial.class);
		verify(materialMapper).insert(captor.capture());
		PayConfigMaterial stored = captor.getValue();
		assertEquals(PayTestFixtures.TENANT, stored.getTenantId());
		assertEquals("config-wx", stored.getConfigId());
		assertEquals("test-key", stored.getKeyId());
		assertFalse(stored.getCiphertext().contains("BEGIN PUBLIC KEY"));
		assertEquals(PayTestFixtures.sha256(PayTestFixtures.resource("synthetic_public_a.pem")),
				stored.getFingerprint());
	}

	@Test
	void missingMasterKeyRejectsUpload() {
		properties.setMasterKey(null);
		MockMultipartFile file = new MockMultipartFile("file", "public.pem", "application/x-pem-file",
				PayTestFixtures.resource("synthetic_public_a.pem"));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.uploadMaterial("config-wx", "WX_PUBLIC_KEY", file));
		assertTrue(exception.getMsg().contains("主密钥未配置"));
		verify(materialMapper, never()).insert(any(PayConfigMaterial.class));
	}

	@Test
	void crossTenantOrMissingConfigIsRejected() {
		MockMultipartFile file = new MockMultipartFile("file", "public.pem", "application/x-pem-file",
				PayTestFixtures.resource("synthetic_public_a.pem"));

		assertThrows(ArynBusinessException.class,
				() -> service.uploadMaterial("config-other-tenant", "WX_PUBLIC_KEY", file));
	}

	@Test
	void purposeMustMatchChannel() {
		MockMultipartFile file = new MockMultipartFile("file", "key.pem", "application/x-pem-file",
				PayTestFixtures.resource("synthetic_key_a.pem"));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.uploadMaterial("config-wx", "ALI_APP_PRIVATE_KEY", file));
		assertEquals("支付材料用途与支付渠道不匹配", exception.getMsg());
	}

	@Test
	void oversizedMaterialIsRejected() {
		properties.setMaxMaterialBytes(10);
		MockMultipartFile file = new MockMultipartFile("file", "public.pem", "application/x-pem-file",
				PayTestFixtures.resource("synthetic_public_a.pem"));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.uploadMaterial("config-wx", "WX_PUBLIC_KEY", file));
		assertTrue(exception.getMsg().contains("不能超过"));
	}

	@Test
	void apiV3KeyMustBeExactly32Bytes() {
		MockMultipartFile file = new MockMultipartFile("file", "apiv3.key", "text/plain",
				"too-short".getBytes(StandardCharsets.UTF_8));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.uploadMaterial("config-wx", "WX_API_V3_KEY", file));
		assertEquals("微信 APIv3 密钥必须为 32 字节", exception.getMsg());
	}

	@Test
	void mismatchedPrivateKeyIsRejectedWhenCertificateAlreadyExists() {
		when(materialMapper.selectOne(any())).thenReturn(certMaterial);
		MockMultipartFile file = new MockMultipartFile("file", "key.pem", "application/x-pem-file",
				PayTestFixtures.resource("synthetic_key_b.pem"));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.uploadMaterial("config-wx", "WX_MERCHANT_PRIVATE_KEY", file));
		assertEquals("证书与私钥不匹配", exception.getMsg());
		verify(materialMapper, never()).insert(any(PayConfigMaterial.class));
	}

	@Test
	void matchedPrivateKeyIsAcceptedButNeverReturned() {
		when(materialMapper.selectOne(any())).thenReturn(null);
		MockMultipartFile file = new MockMultipartFile("file", "key.pem", "application/x-pem-file",
				PayTestFixtures.resource("synthetic_key_a.pem"));

		var uploaded = service.uploadMaterial("config-wx", "WX_MERCHANT_PRIVATE_KEY", file);

		assertTrue(uploaded.getSensitive());
		assertFalse(uploaded.toString().contains("BEGIN PRIVATE KEY"));
	}

	@Test
	void malformedCertificateIsRejectedWithReadableReason() {
		MockMultipartFile file = new MockMultipartFile("file", "cert.pem", "application/x-pem-file",
				"garbage".getBytes(StandardCharsets.UTF_8));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.uploadMaterial("config-wx", "WX_MERCHANT_CERT", file));
		assertEquals("证书格式无法解析", exception.getMsg());
	}

	private PayConfig wechatConfig() {
		return new PayConfig().setId("config-wx")
			.setTenantId(PayTestFixtures.TENANT)
			.setType("1")
			.setTerminalType("0")
			.setAppId("wx-synthetic-app")
			.setMchId("synthetic-mch");
	}

}
