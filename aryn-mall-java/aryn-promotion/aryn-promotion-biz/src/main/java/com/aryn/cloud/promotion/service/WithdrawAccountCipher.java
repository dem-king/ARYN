package com.aryn.cloud.promotion.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Component
public class WithdrawAccountCipher {

	private static final String PREFIX = "v1:";
	private static final int NONCE_LENGTH = 12;
	private static final int TAG_BITS = 128;

	private final SecureRandom secureRandom = new SecureRandom();

	private final SecretKeySpec key;

	public WithdrawAccountCipher(@Value("${distribution.withdraw.account-key:}") String encodedKey) {
		if (encodedKey == null || encodedKey.isBlank()) {
			this.key = null;
			return;
		}
		byte[] keyBytes;
		try {
			keyBytes = Base64.getDecoder().decode(encodedKey);
		}
		catch (IllegalArgumentException e) {
			throw new IllegalStateException("提现账号加密密钥必须使用Base64编码", e);
		}
		if (keyBytes.length != 32) {
			throw new IllegalStateException("提现账号加密密钥必须为32字节");
		}
		this.key = new SecretKeySpec(keyBytes, "AES");
	}

	public String encrypt(String plaintext) {
		requireKey();
		if (plaintext == null || plaintext.isBlank()) {
			throw new ArynBusinessException("收款账号不能为空");
		}
		byte[] nonce = new byte[NONCE_LENGTH];
		secureRandom.nextBytes(nonce);
		try {
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
			byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
			byte[] payload = ByteBuffer.allocate(nonce.length + ciphertext.length)
				.put(nonce)
				.put(ciphertext)
				.array();
			return PREFIX + Base64.getEncoder().encodeToString(payload);
		}
		catch (GeneralSecurityException e) {
			throw new ArynBusinessException("收款账号加密失败");
		}
	}

	public String decrypt(String encrypted) {
		requireKey();
		if (encrypted == null || !encrypted.startsWith(PREFIX)) {
			throw new ArynBusinessException("收款账号密文格式无效");
		}
		try {
			byte[] payload = Base64.getDecoder().decode(encrypted.substring(PREFIX.length()));
			if (payload.length <= NONCE_LENGTH) {
				throw new GeneralSecurityException("invalid payload");
			}
			byte[] nonce = new byte[NONCE_LENGTH];
			byte[] ciphertext = new byte[payload.length - NONCE_LENGTH];
			System.arraycopy(payload, 0, nonce, 0, nonce.length);
			System.arraycopy(payload, nonce.length, ciphertext, 0, ciphertext.length);
			Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
			cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
			return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
		}
		catch (GeneralSecurityException | IllegalArgumentException e) {
			throw new ArynBusinessException("收款账号解密失败");
		}
	}

	public String decryptAndMask(String encrypted) {
		String plaintext = decrypt(encrypted);
		return mask(plaintext);
	}

	public String maskStored(String stored) {
		if (stored == null || stored.isBlank()) {
			return stored;
		}
		return stored.startsWith(PREFIX) ? decryptAndMask(stored) : mask(stored);
	}

	private String mask(String plaintext) {
		int visible = Math.min(4, plaintext.length());
		return "****" + plaintext.substring(plaintext.length() - visible);
	}

	private void requireKey() {
		if (key == null) {
			throw new ArynBusinessException("未配置提现账号加密密钥");
		}
	}
}
