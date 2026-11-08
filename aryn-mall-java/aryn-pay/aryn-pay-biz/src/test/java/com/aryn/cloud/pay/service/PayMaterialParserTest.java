package com.aryn.cloud.pay.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.PayTestFixtures;
import com.aryn.cloud.pay.api.enums.PayConfigMaterialPurposeEnum;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PayMaterialParserTest {

	private final PayMaterialParser parser = new PayMaterialParser();

	@Test
	void parsesSyntheticPrivateKeyCertificateAndPublicKey() {
		PrivateKey privateKey = parser.parsePrivateKey(PayTestFixtures.resource("synthetic_key_a.pem"));
		X509Certificate certificate = parser
			.parseCertificate(PayTestFixtures.resource("synthetic_cert_a.pem"));
		PublicKey publicKey = parser.parsePublicKey(PayTestFixtures.resource("synthetic_public_a.pem"));

		assertNotNull(privateKey);
		assertNotNull(publicKey);
		assertEquals(2048, ((java.security.interfaces.RSAPrivateKey) privateKey).getModulus().bitLength());
		assertTrue(certificate.getSubjectX500Principal().getName().contains("SYNTHETIC PAY TEST A"));
		assertTrue(certificate.getNotAfter().toInstant().isAfter(java.time.Instant.now()));
	}

	@Test
	void acceptsTraditionalPkcs1PrivateKey() {
		PrivateKey pkcs1 = parser.parsePrivateKey(PayTestFixtures.resource("synthetic_key_a_pkcs1.pem"));
		X509Certificate certificate = parser
			.parseCertificate(PayTestFixtures.resource("synthetic_cert_a.pem"));

		assertTrue(parser.keyPairMatches(
				new String(PayTestFixtures.resource("synthetic_key_a_pkcs1.pem"), StandardCharsets.UTF_8)
					.getBytes(StandardCharsets.UTF_8),
				certificate));
		assertNotNull(pkcs1);
	}

	@Test
	void keyPairMustMatchCertificate() {
		X509Certificate certificateA = parser.parseCertificate(PayTestFixtures.resource("synthetic_cert_a.pem"));

		assertTrue(parser.keyPairMatches(PayTestFixtures.resource("synthetic_key_a.pem"), certificateA));
		assertFalse(parser.keyPairMatches(PayTestFixtures.resource("synthetic_key_b.pem"), certificateA));
	}

	@Test
	void expiredCertificateIsRejected() {
		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> parser.parseCertificate(PayTestFixtures.resource("synthetic_cert_expired.pem")));
		assertEquals("证书已过期", exception.getMsg());
	}

	@Test
	void malformedCertificateAndPrivateKeyAreRejectedWithoutEchoingContent() {
		byte[] garbage = "not-a-certificate".getBytes(StandardCharsets.UTF_8);
		ArynBusinessException cert = assertThrows(ArynBusinessException.class,
				() -> parser.parseCertificate(garbage));
		assertEquals("证书格式无法解析", cert.getMsg());
		assertFalse(cert.getMsg().contains("not-a-certificate"));

		ArynBusinessException key = assertThrows(ArynBusinessException.class,
				() -> parser.parsePrivateKey(garbage));
		assertTrue(key.getMsg().contains("私钥格式无法解析"));
	}

	@Test
	void parsesApiV3KeyPurposeWithoutTreatingItAsCertificate() {
		PayMaterialParser.ParsedMaterial parsed = parser.parse(PayConfigMaterialPurposeEnum.WX_API_V3_KEY,
				PayTestFixtures.resource("synthetic_apiv3.key"));

		assertEquals("RAW", parsed.getContentType());
		assertEquals(32, parsed.getByteSize());
	}

}
