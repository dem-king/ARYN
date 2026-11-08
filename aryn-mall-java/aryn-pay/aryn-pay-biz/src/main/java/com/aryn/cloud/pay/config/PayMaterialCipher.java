
package com.aryn.cloud.pay.config;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 支付材料 AES-256-GCM 加解密。
 *
 * <p>AAD 绑定结构版本、租户、材料 ID 与用途：交换两行密文、改用途或跨租户读取都会
 * 解密失败，而不是返回另一份材料。每次加密使用独立随机 nonce，相同明文得到不同密文。
 */
@Component
@RequiredArgsConstructor
public class PayMaterialCipher {

	private static final String ALGORITHM = "AES-256-GCM";

	private static final String TRANSFORMATION = "AES/GCM/NoPadding";

	private static final int NONCE_BYTES = 12;

	private static final int TAG_BITS = 128;

	private static final int KEY_BYTES = 32;

	private static final String AAD_VERSION = "v1";

	private final PayCredentialProperties properties;

	private final SecureRandom secureRandom = new SecureRandom();

	private volatile Map<String, byte[]> keyCache;

	public boolean isConfigured() {
		try {
			return !keys().isEmpty();
		}
		catch (RuntimeException exception) {
			return false;
		}
	}

	public EncryptedMaterial encrypt(String tenantId, String materialId, String purpose, byte[] plaintext) {
		if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(materialId) || !StringUtils.hasText(purpose)) {
			throw new ArynBusinessException("支付材料缺少租户、材料或用途");
		}
		byte[] key = keysOrThrow().get(properties.getKeyId());
		if (key == null) {
			throw new ArynBusinessException("支付材料主密钥未配置");
		}
		byte[] nonce = new byte[NONCE_BYTES];
		secureRandom.nextBytes(nonce);
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, nonce));
			cipher.updateAAD(aad(tenantId, materialId, purpose));
			byte[] ciphertext = cipher.doFinal(plaintext);
			return new EncryptedMaterial(properties.getKeyId(), Base64.getEncoder().encodeToString(nonce),
					Base64.getEncoder().encodeToString(ciphertext), ALGORITHM);
		}
		catch (RuntimeException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new ArynBusinessException("支付材料加密失败");
		}
	}

	public byte[] decrypt(String tenantId, String materialId, String purpose, String keyId, String nonce,
			String ciphertext) {
		if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(materialId) || !StringUtils.hasText(purpose)
				|| !StringUtils.hasText(keyId) || !StringUtils.hasText(nonce) || !StringUtils.hasText(ciphertext)) {
			throw new ArynBusinessException("支付材料密文不完整");
		}
		byte[] key = keysOrThrow().get(keyId);
		if (key == null) {
			throw new ArynBusinessException("支付材料加密密钥不可用");
		}
		try {
			Cipher cipher = Cipher.getInstance(TRANSFORMATION);
			cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
					new GCMParameterSpec(TAG_BITS, Base64.getDecoder().decode(nonce)));
			cipher.updateAAD(aad(tenantId, materialId, purpose));
			return cipher.doFinal(Base64.getDecoder().decode(ciphertext));
		}
		catch (RuntimeException | java.security.GeneralSecurityException exception) {
			throw new ArynBusinessException("支付材料解密失败");
		}
	}

	public String fingerprint(byte[] plaintext) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(plaintext);
			return java.util.HexFormat.of().formatHex(digest);
		}
		catch (Exception exception) {
			throw new ArynBusinessException("支付材料指纹计算失败");
		}
	}

	private byte[] aad(String tenantId, String materialId, String purpose) {
		return (AAD_VERSION + "|" + tenantId + "|" + materialId + "|" + purpose).getBytes(StandardCharsets.UTF_8);
	}

	private Map<String, byte[]> keysOrThrow() {
		try {
			return keys();
		}
		catch (RuntimeException exception) {
			throw new ArynBusinessException("支付材料主密钥未配置");
		}
	}

	private Map<String, byte[]> keys() {
		Map<String, byte[]> current = keyCache;
		if (current != null) {
			return current;
		}
		synchronized (this) {
			if (keyCache == null) {
				keyCache = parseKeys();
			}
			return keyCache;
		}
	}

	private Map<String, byte[]> parseKeys() {
		Map<String, byte[]> keys = new LinkedHashMap<>();
		if (StringUtils.hasText(properties.getMasterKey())) {
			keys.put(properties.getKeyId(), decodeKey(properties.getMasterKey()));
		}
		if (StringUtils.hasText(properties.getPreviousKeys())) {
			for (String item : properties.getPreviousKeys().split(",")) {
				String trimmed = item.trim();
				if (trimmed.isEmpty()) {
					continue;
				}
				int separator = trimmed.indexOf('=');
				if (separator <= 0 || separator == trimmed.length() - 1) {
					throw new IllegalStateException("支付材料历史主密钥格式错误，应为 keyId=Base64");
				}
				keys.put(trimmed.substring(0, separator).trim(), decodeKey(trimmed.substring(separator + 1).trim()));
			}
		}
		return Map.copyOf(keys);
	}

	private byte[] decodeKey(String value) {
		try {
			byte[] decoded = Base64.getDecoder().decode(value.trim());
			if (decoded.length != KEY_BYTES) {
				throw new IllegalStateException("支付材料主密钥必须是 32 字节");
			}
			return decoded;
		}
		catch (IllegalArgumentException exception) {
			throw new IllegalStateException("支付材料主密钥不是合法 Base64", exception);
		}
	}

	@Getter
	@RequiredArgsConstructor
	public static class EncryptedMaterial {

		private final String keyId;

		private final String nonce;

		private final String ciphertext;

		private final String algorithm;

	}

}
