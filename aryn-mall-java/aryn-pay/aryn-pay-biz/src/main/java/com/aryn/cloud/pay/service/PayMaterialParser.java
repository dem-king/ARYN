
package com.aryn.cloud.pay.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.pay.api.enums.PayConfigMaterialPurposeEnum;
import com.aryn.cloud.pay.api.enums.PayChannelEnum;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

/**
 * 支付材料本地解析。只证明材料内部一致，不证明平台已登记或产品已开通。
 */
@Component
public class PayMaterialParser {

	private static final byte[] PAIR_CHECK_CONTENT = "aryn-pay-material-pair-check"
		.getBytes(StandardCharsets.UTF_8);

	private static final int MIN_RSA_BITS = 2048;

	public ParsedMaterial parse(PayConfigMaterialPurposeEnum purpose, byte[] content) {
		if (purpose == null) {
			throw new ArynBusinessException("支付材料用途不能为空");
		}
		if (content == null || content.length == 0) {
			throw new ArynBusinessException("支付材料内容为空");
		}
		if (purpose == PayConfigMaterialPurposeEnum.WX_API_V3_KEY) {
			return ParsedMaterial.builder().contentType("RAW").byteSize(content.length).build();
		}
		if (isPrivateKeyPurpose(purpose)) {
			PrivateKey privateKey = parsePrivateKey(content);
			return ParsedMaterial.builder().contentType("PEM").byteSize(content.length).keySize(keySize(privateKey))
				.build();
		}
		if (purpose == PayConfigMaterialPurposeEnum.WX_PUBLIC_KEY) {
			PublicKey publicKey = parsePublicKey(content);
			return ParsedMaterial.builder().contentType("PEM").byteSize(content.length).keySize(keySize(publicKey))
				.build();
		}
		X509Certificate certificate = parseCertificate(content);
		return ParsedMaterial.builder()
			.contentType(isPem(content) ? "PEM" : "DER")
			.byteSize(content.length)
			.certSerialNo(certificate.getSerialNumber().toString(16).toUpperCase())
			.certNotBefore(LocalDateTime.ofInstant(certificate.getNotBefore().toInstant(), ZoneId.systemDefault()))
			.certNotAfter(LocalDateTime.ofInstant(certificate.getNotAfter().toInstant(), ZoneId.systemDefault()))
			.keySize(keySize(certificate.getPublicKey()))
			.build();
	}

	/**
	 * 校验材料与用途、渠道匹配。
	 */
	public void assertChannelMatches(PayConfigMaterialPurposeEnum purpose, PayChannelEnum channel) {
		if (purpose.getChannel() != channel) {
			throw new ArynBusinessException("支付材料用途与支付渠道不匹配");
		}
	}

	public boolean keyPairMatches(byte[] privateKeyContent, X509Certificate certificate) {
		try {
			PrivateKey privateKey = parsePrivateKey(privateKeyContent);
			Signature signer = Signature.getInstance("SHA256withRSA");
			signer.initSign(privateKey);
			signer.update(PAIR_CHECK_CONTENT);
			byte[] signature = signer.sign();
			Signature verifier = Signature.getInstance("SHA256withRSA");
			verifier.initVerify(certificate.getPublicKey());
			verifier.update(PAIR_CHECK_CONTENT);
			return verifier.verify(signature);
		}
		catch (Exception exception) {
			return false;
		}
	}

	public X509Certificate parseCertificate(byte[] content) {
		try {
			CertificateFactory factory = CertificateFactory.getInstance("X.509");
			byte[] der = isPem(content) ? pemBody(content) : content;
			X509Certificate certificate = (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(der));
			certificate.checkValidity();
			if (certificate.getPublicKey() instanceof RSAPublicKey rsaPublicKey
					&& rsaPublicKey.getModulus().bitLength() < MIN_RSA_BITS) {
				throw new ArynBusinessException("证书 RSA 密钥长度不足 2048 位");
			}
			return certificate;
		}
		catch (ArynBusinessException exception) {
			throw exception;
		}
		catch (java.security.cert.CertificateExpiredException exception) {
			throw new ArynBusinessException("证书已过期");
		}
		catch (java.security.cert.CertificateNotYetValidException exception) {
			throw new ArynBusinessException("证书尚未生效");
		}
		catch (Exception exception) {
			throw new ArynBusinessException("证书格式无法解析");
		}
	}

	public PrivateKey parsePrivateKey(byte[] content) {
		try {
			byte[] der = isPem(content) ? pemBody(content) : content;
			try {
				PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
				assertRsaStrength(key);
				return key;
			}
			catch (java.security.spec.InvalidKeySpecException ignored) {
				// 兼容 traditional PKCS#1（BEGIN RSA PRIVATE KEY）
				byte[] pkcs8 = wrapPkcs1PrivateKey(der);
				PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
				assertRsaStrength(key);
				return key;
			}
		}
		catch (ArynBusinessException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new ArynBusinessException("私钥格式无法解析，请上传 PKCS#8 或 PKCS#1 RSA 私钥");
		}
	}

	public PublicKey parsePublicKey(byte[] content) {
		try {
			byte[] der = isPem(content) ? pemBody(content) : content;
			try {
				PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
				assertRsaStrength(key);
				return key;
			}
			catch (java.security.spec.InvalidKeySpecException ignored) {
				byte[] spki = wrapPkcs1PublicKey(der);
				PublicKey key = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(spki));
				assertRsaStrength(key);
				return key;
			}
		}
		catch (ArynBusinessException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new ArynBusinessException("公钥格式无法解析");
		}
	}

	public boolean isPem(byte[] content) {
		String text = new String(content, StandardCharsets.US_ASCII);
		return text.contains("-----BEGIN");
	}

	private void assertRsaStrength(PrivateKey key) {
		if (key instanceof RSAPrivateKey rsaPrivateKey && rsaPrivateKey.getModulus().bitLength() < MIN_RSA_BITS) {
			throw new ArynBusinessException("私钥 RSA 密钥长度不足 2048 位");
		}
	}

	private void assertRsaStrength(PublicKey key) {
		if (key instanceof RSAPublicKey rsaPublicKey && rsaPublicKey.getModulus().bitLength() < MIN_RSA_BITS) {
			throw new ArynBusinessException("公钥 RSA 密钥长度不足 2048 位");
		}
	}

	private int keySize(PrivateKey key) {
		return key instanceof RSAPrivateKey rsa ? rsa.getModulus().bitLength() : 0;
	}

	private int keySize(PublicKey key) {
		return key instanceof RSAPublicKey rsa ? rsa.getModulus().bitLength() : 0;
	}

	private boolean isPrivateKeyPurpose(PayConfigMaterialPurposeEnum purpose) {
		return purpose == PayConfigMaterialPurposeEnum.WX_MERCHANT_PRIVATE_KEY
				|| purpose == PayConfigMaterialPurposeEnum.ALI_APP_PRIVATE_KEY;
	}

	private byte[] pemBody(byte[] content) {
		String text = new String(content, StandardCharsets.US_ASCII);
		StringBuilder base64 = new StringBuilder();
		for (String line : text.split("\\R")) {
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("-----")) {
				continue;
			}
			base64.append(trimmed);
		}
		if (!StringUtils.hasText(base64)) {
			throw new ArynBusinessException("PEM 内容为空");
		}
		try {
			return Base64.getDecoder().decode(base64.toString());
		}
		catch (IllegalArgumentException exception) {
			throw new ArynBusinessException("PEM Base64 内容非法");
		}
	}

	/**
	 * PKCS#1 RSAPrivateKey -> PKCS#8 PrivateKeyInfo，避免运行时写临时文件。
	 */
	private byte[] wrapPkcs1PrivateKey(byte[] pkcs1) {
		byte[] version = { 0x02, 0x01, 0x00 };
		byte[] algorithm = der(0x30, concat(
				new byte[] { 0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x01 },
				new byte[] { 0x05, 0x00 }));
		byte[] privateKey = der(0x04, pkcs1);
		return der(0x30, concat(version, algorithm, privateKey));
	}

	/**
	 * PKCS#1 RSAPublicKey -> SubjectPublicKeyInfo。
	 */
	private byte[] wrapPkcs1PublicKey(byte[] pkcs1) {
		byte[] algorithm = der(0x30, concat(
				new byte[] { 0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x01 },
				new byte[] { 0x05, 0x00 }));
		byte[] bitString = der(0x03, concat(new byte[] { 0x00 }, pkcs1));
		return der(0x30, concat(algorithm, bitString));
	}

	private byte[] der(int tag, byte[] content) {
		byte[] length = derLength(content.length);
		byte[] result = new byte[1 + length.length + content.length];
		result[0] = (byte) tag;
		System.arraycopy(length, 0, result, 1, length.length);
		System.arraycopy(content, 0, result, 1 + length.length, content.length);
		return result;
	}

	private byte[] derLength(int length) {
		if (length < 0x80) {
			return new byte[] { (byte) length };
		}
		int bytes = 0;
		int value = length;
		while (value > 0) {
			bytes++;
			value >>= 8;
		}
		byte[] result = new byte[bytes + 1];
		result[0] = (byte) (0x80 | bytes);
		for (int index = bytes; index > 0; index--) {
			result[index] = (byte) (length & 0xFF);
			length >>= 8;
		}
		return result;
	}

	private byte[] concat(byte[]... arrays) {
		int length = 0;
		for (byte[] array : arrays) {
			length += array.length;
		}
		byte[] result = new byte[length];
		int offset = 0;
		for (byte[] array : arrays) {
			System.arraycopy(array, 0, result, offset, array.length);
			offset += array.length;
		}
		return result;
	}

	@Getter
	@Builder
	public static class ParsedMaterial {

		private String contentType;

		private Integer byteSize;

		private String certSerialNo;

		private LocalDateTime certNotBefore;

		private LocalDateTime certNotAfter;

		private Integer keySize;

	}

}
