package com.aryn.cloud.pay;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.pay.api.entity.PayConfig;
import com.aryn.cloud.pay.api.entity.PayConfigMaterial;
import com.aryn.cloud.pay.api.entity.PayConfigRevision;
import com.aryn.cloud.pay.api.enums.PayChannelEnum;
import com.aryn.cloud.pay.api.enums.PayConfigMaterialPurposeEnum;
import com.aryn.cloud.pay.config.PayCredentialProperties;
import com.aryn.cloud.pay.config.PayMaterialCipher;
import com.aryn.cloud.pay.service.PayResolvedIdentity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 测试夹具：全部为合成密钥/证书，禁止替换为任何真实商户材料。
 */
public final class PayTestFixtures {

	public static final String TENANT = "tenant-synthetic";

	private PayTestFixtures() {
	}

	public static PayCredentialProperties properties() {
		PayCredentialProperties properties = new PayCredentialProperties();
		properties.setMasterKey(Base64.getEncoder()
			.encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
		properties.setKeyId("test-key");
		return properties;
	}

	public static PayMaterialCipher cipher() {
		return new PayMaterialCipher(properties());
	}

	public static byte[] resource(String name) {
		try (InputStream input = PayTestFixtures.class.getResourceAsStream("/pay/" + name)) {
			if (input == null) {
				throw new IllegalStateException("缺少测试夹具: " + name);
			}
			return input.readAllBytes();
		}
		catch (IOException exception) {
			throw new IllegalStateException("读取测试夹具失败: " + name, exception);
		}
	}

	public static String text(String name) {
		return new String(resource(name), StandardCharsets.UTF_8);
	}

	public static PayResolvedIdentity wechatIdentity(String revisionId) {
		Map<String, byte[]> materials = new LinkedHashMap<>();
		materials.put(PayConfigMaterialPurposeEnum.WX_API_V3_KEY.getCode(), resource("synthetic_apiv3.key"));
		materials.put(PayConfigMaterialPurposeEnum.WX_MERCHANT_PRIVATE_KEY.getCode(), resource("synthetic_key_a.pem"));
		materials.put(PayConfigMaterialPurposeEnum.WX_MERCHANT_CERT.getCode(), resource("synthetic_cert_a.pem"));
		materials.put(PayConfigMaterialPurposeEnum.WX_PUBLIC_KEY.getCode(), resource("synthetic_public_a.pem"));
		return PayResolvedIdentity.builder()
			.tenantId(TENANT)
			.configId("config-1")
			.revisionId(revisionId)
			.channel(PayChannelEnum.WECHAT.getCode())
			.terminalType("0")
			.appId("wx-synthetic-app")
			.mchId("synthetic-mch")
			.environment("prod")
			.wxPublicKeyId("PUB_KEY_ID_SYNTHETIC")
			.materials(Map.copyOf(materials))
			.fingerprints(fingerprints(materials))
			.build();
	}

	public static PayResolvedIdentity alipayIdentity(String revisionId) {
		Map<String, byte[]> materials = new LinkedHashMap<>();
		materials.put(PayConfigMaterialPurposeEnum.ALI_APP_PRIVATE_KEY.getCode(), resource("synthetic_key_a.pem"));
		materials.put(PayConfigMaterialPurposeEnum.ALI_APP_CERT.getCode(), resource("synthetic_cert_a.pem"));
		materials.put(PayConfigMaterialPurposeEnum.ALI_PUBLIC_CERT.getCode(), resource("synthetic_cert_a.pem"));
		materials.put(PayConfigMaterialPurposeEnum.ALI_ROOT_CERT.getCode(), resource("synthetic_cert_a.pem"));
		return PayResolvedIdentity.builder()
			.tenantId(TENANT)
			.configId("config-2")
			.revisionId(revisionId)
			.channel(PayChannelEnum.ALIPAY.getCode())
			.terminalType("2")
			.appId("ali-synthetic-app")
			.mchId("2088000000000000")
			.environment("prod")
			.materials(Map.copyOf(materials))
			.fingerprints(fingerprints(materials))
			.build();
	}

	public static PayResolvedIdentity alipayMiniIdentity(String revisionId, String buyerIdentityMode) {
		Map<String, byte[]> materials = new LinkedHashMap<>();
		materials.put(PayConfigMaterialPurposeEnum.ALI_APP_PRIVATE_KEY.getCode(), resource("synthetic_key_a.pem"));
		materials.put(PayConfigMaterialPurposeEnum.ALI_APP_CERT.getCode(), resource("synthetic_cert_a.pem"));
		materials.put(PayConfigMaterialPurposeEnum.ALI_PUBLIC_CERT.getCode(), resource("synthetic_cert_a.pem"));
		materials.put(PayConfigMaterialPurposeEnum.ALI_ROOT_CERT.getCode(), resource("synthetic_cert_a.pem"));
		return PayResolvedIdentity.builder()
			.tenantId(TENANT)
			.configId("config-3")
			.revisionId(revisionId)
			.channel(PayChannelEnum.ALIPAY.getCode())
			.terminalType("0")
			.appId("2021000000000001")
			.mchId("2088000000000000")
			.environment("prod")
			.buyerIdentityMode(buyerIdentityMode)
			.materials(Map.copyOf(materials))
			.fingerprints(fingerprints(materials))
			.build();
	}

	public static Map<String, String> fingerprints(Map<String, byte[]> materials) {
		Map<String, String> result = new LinkedHashMap<>();
		materials.forEach((purpose, content) -> result.put(purpose, sha256(content)));
		return Map.copyOf(result);
	}

	public static String sha256(byte[] content) {
		try {
			return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
	}

	public static PayConfig config(String id, String channel, String terminal) {
		return new PayConfig().setId(id)
			.setTenantId(TENANT)
			.setType(channel)
			.setTerminalType(terminal)
			.setAppId("app-" + id)
			.setMchId("mch-" + id)
			.setEnabled("1")
			.setActiveRevisionId("revision-" + id)
			.setConfigVersion(0);
	}

	public static PayConfigRevision revision(String id, String configId, String channel, String terminal,
			String appId, String mchId, String refs) {
		return new PayConfigRevision().setId(id)
			.setTenantId(TENANT)
			.setConfigId(configId)
			.setRevisionNo(1)
			.setChannel(channel)
			.setTerminalType(terminal)
			.setAppId(appId)
			.setMchId(mchId)
			.setEnvironment("prod")
			.setWxPublicKeyId("PUB_KEY_ID_SYNTHETIC")
			.setMaterialRefs(refs)
			.setIdentityHash(sha256(id.getBytes(StandardCharsets.UTF_8)))
			.setCheckStatus("1");
	}

	public static PayConfigMaterial encryptedMaterial(PayMaterialCipher cipher, String id, String configId,
			PayConfigMaterialPurposeEnum purpose, byte[] content) {
		PayMaterialCipher.EncryptedMaterial encrypted = cipher.encrypt(TENANT, id, purpose.getCode(), content);
		return new PayConfigMaterial().setId(id)
			.setTenantId(TENANT)
			.setConfigId(configId)
			.setPurpose(purpose.getCode())
			.setCiphertext(encrypted.getCiphertext())
			.setNonce(encrypted.getNonce())
			.setKeyId(encrypted.getKeyId())
			.setAlgorithm(encrypted.getAlgorithm())
			.setFingerprint(sha256(content))
			.setContentType("PEM")
			.setByteSize(content.length);
	}

	public static void tenant(String tenantId) {
		ArynTenantContextHolder.setTenantId(tenantId);
	}

	public static void clearTenant() {
		ArynTenantContextHolder.removeTenantId();
	}

}
