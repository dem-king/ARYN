package com.aryn.cloud.promotion.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.security.SecureRandom;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class WithdrawAccountCipherTest {

	@Test
	void aesGcmCipherRoundTripsWithRandomNonce() throws Exception {
		byte[] key = new byte[32];
		new SecureRandom().nextBytes(key);
		Class<?> cipherType = Class.forName("com.aryn.cloud.promotion.service.WithdrawAccountCipher");
		Object cipher = cipherType.getConstructor(String.class)
			.newInstance(Base64.getEncoder().encodeToString(key));
		Method encrypt = cipherType.getMethod("encrypt", String.class);
		Method decrypt = cipherType.getMethod("decrypt", String.class);

		String first = (String) encrypt.invoke(cipher, "6222021234567890");
		String second = (String) encrypt.invoke(cipher, "6222021234567890");

		assertThat(first).startsWith("v1:").doesNotContain("6222021234567890");
		assertThat(second).isNotEqualTo(first);
		assertThat(decrypt.invoke(cipher, first)).isEqualTo("6222021234567890");
	}
}
