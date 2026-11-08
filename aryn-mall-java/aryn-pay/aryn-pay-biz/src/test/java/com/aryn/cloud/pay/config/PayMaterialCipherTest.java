package com.aryn.cloud.pay.config;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.PayTestFixtures;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayMaterialCipherTest {

	private final PayMaterialCipher cipher = PayTestFixtures.cipher();

	@Test
	void samePlaintextProducesDifferentNonceAndCiphertext() {
		byte[] plaintext = "BEGIN SYNTHETIC MATERIAL".getBytes(StandardCharsets.UTF_8);

		PayMaterialCipher.EncryptedMaterial first = cipher.encrypt("tenant-1", "material-1", "WX_PUBLIC_KEY",
				plaintext);
		PayMaterialCipher.EncryptedMaterial second = cipher.encrypt("tenant-1", "material-2", "WX_PUBLIC_KEY",
				plaintext);

		assertNotEquals(first.getNonce(), second.getNonce());
		assertNotEquals(first.getCiphertext(), second.getCiphertext());
		assertEquals("test-key", first.getKeyId());
		assertEquals("AES-256-GCM", first.getAlgorithm());
	}

	@Test
	void decryptRequiresExactTenantMaterialAndPurposeAad() {
		byte[] plaintext = "synthetic-secret".getBytes(StandardCharsets.UTF_8);
		PayMaterialCipher.EncryptedMaterial encrypted = cipher.encrypt("tenant-1", "material-1", "WX_PUBLIC_KEY",
				plaintext);

		assertArrayEquals(plaintext, cipher.decrypt("tenant-1", "material-1", "WX_PUBLIC_KEY", encrypted.getKeyId(),
				encrypted.getNonce(), encrypted.getCiphertext()));
		assertThrows(ArynBusinessException.class, () -> cipher.decrypt("tenant-2", "material-1", "WX_PUBLIC_KEY",
				encrypted.getKeyId(), encrypted.getNonce(), encrypted.getCiphertext()));
		assertThrows(ArynBusinessException.class, () -> cipher.decrypt("tenant-1", "material-2", "WX_PUBLIC_KEY",
				encrypted.getKeyId(), encrypted.getNonce(), encrypted.getCiphertext()));
		assertThrows(ArynBusinessException.class, () -> cipher.decrypt("tenant-1", "material-1", "ALI_APP_CERT",
				encrypted.getKeyId(), encrypted.getNonce(), encrypted.getCiphertext()));
	}

	@Test
	void missingMasterKeyRejectsEncryptAndDecrypt() {
		PayMaterialCipher unconfigured = new PayMaterialCipher(new PayCredentialProperties());

		assertFalse(unconfigured.isConfigured());
		ArynBusinessException encrypt = assertThrows(ArynBusinessException.class,
				() -> unconfigured.encrypt("tenant-1", "material-1", "WX_PUBLIC_KEY", new byte[] { 1 }));
		assertEquals("支付材料主密钥未配置", encrypt.getMsg());
		ArynBusinessException decrypt = assertThrows(ArynBusinessException.class,
				() -> unconfigured.decrypt("tenant-1", "material-1", "WX_PUBLIC_KEY", "test-key", "bm9uY2U=",
						"Y2lwaGVy"));
		assertEquals("支付材料加密密钥不可用", decrypt.getMsg());
	}

	@Test
	void unknownKeyIdIsRejected() {
		PayMaterialCipher.EncryptedMaterial encrypted = cipher.encrypt("tenant-1", "material-1", "WX_PUBLIC_KEY",
				"value".getBytes(StandardCharsets.UTF_8));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> cipher.decrypt("tenant-1", "material-1", "WX_PUBLIC_KEY", "retired-key",
						encrypted.getNonce(), encrypted.getCiphertext()));
		assertEquals("支付材料加密密钥不可用", exception.getMsg());
	}

	@Test
	void previousKeyCanDecryptAfterRotation() {
		String oldKey = Base64.getEncoder()
			.encodeToString("abcdef0123456789abcdef0123456789".getBytes(StandardCharsets.UTF_8));
		PayCredentialProperties oldProperties = new PayCredentialProperties();
		oldProperties.setMasterKey(oldKey);
		oldProperties.setKeyId("old-key");
		PayMaterialCipher.EncryptedMaterial encrypted = new PayMaterialCipher(oldProperties)
			.encrypt("tenant-1", "material-1", "WX_PUBLIC_KEY", "value".getBytes(StandardCharsets.UTF_8));

		PayCredentialProperties rotated = PayTestFixtures.properties();
		rotated.setPreviousKeys("old-key=" + oldKey);
		byte[] plaintext = new PayMaterialCipher(rotated).decrypt("tenant-1", "material-1", "WX_PUBLIC_KEY",
				encrypted.getKeyId(), encrypted.getNonce(), encrypted.getCiphertext());

		assertEquals("value", new String(plaintext, StandardCharsets.UTF_8));
		assertTrue(new PayMaterialCipher(rotated).isConfigured());
	}

}
