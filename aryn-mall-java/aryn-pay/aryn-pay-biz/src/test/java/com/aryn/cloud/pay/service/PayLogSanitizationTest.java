package com.aryn.cloud.pay.service;

import com.aryn.cloud.common.log.aop.LogAopAspect;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 操作审计参数摘要不得记录支付私钥或密文。
 */
class PayLogSanitizationTest {

	@Test
	void multipartMaterialIsOmittedFromAuditParams() throws Exception {
		MockMultipartFile file = new MockMultipartFile("file", "apiclient_key.pem", "application/x-pem-file",
				"-----BEGIN PRIVATE KEY-----\nSYNTHETIC-SECRET\n-----END PRIVATE KEY-----".getBytes());
		String json = safeParams(new Object[] { "config-1", "WX_MERCHANT_PRIVATE_KEY", file });

		assertFalse(json.contains("PRIVATE KEY"));
		assertFalse(json.contains("SYNTHETIC-SECRET"));
		assertTrue(json.contains("[binary omitted]"));
	}

	@Test
	void secretLikeFieldsAreRedacted() throws Exception {
		Map<String, String> payload = new LinkedHashMap<>();
		payload.put("mchKey", "synthetic-private-key");
		payload.put("apiv3Key", "synthetic-api-v3-key");
		payload.put("ciphertext", "synthetic-ciphertext");
		String json = safeParams(new Object[] { payload });

		assertFalse(json.contains("synthetic-private-key"));
		assertFalse(json.contains("synthetic-api-v3-key"));
		assertFalse(json.contains("synthetic-ciphertext"));
		assertTrue(json.contains("***"));
	}

	private String safeParams(Object[] args) throws Exception {
		LogAopAspect aspect = new LogAopAspect(null, null);
		Method method = LogAopAspect.class.getDeclaredMethod("safeParams", Object[].class);
		method.setAccessible(true);
		return (String) method.invoke(aspect, (Object) args);
	}

}
