package com.aryn.cloud.pay.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.pay.api.vo.PayConfigVO;
import com.aryn.cloud.pay.service.IPayConfigService;
import com.aryn.cloud.pay.service.PayConfigCheckService;
import com.aryn.cloud.pay.service.PayConfigRevisionService;
import com.aryn.cloud.pay.service.PayCredentialService;
import com.aryn.cloud.pay.service.PayLegacyMigrationService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PayConfigControllerTest {

	@Test
	void uploadEnableCheckAndMigrationHaveIndependentPermissions() throws NoSuchMethodException {
		assertPermission("uploadMaterial", new Class[] { String.class, String.class, MultipartFile.class },
				"pay:payconfig:material");
		assertPermission("createRevision", new Class[] { String.class,
				com.aryn.cloud.pay.api.dto.PayConfigRevisionCreateReqDTO.class }, "pay:payconfig:material");
		assertPermission("publish", new Class[] { String.class, String.class, Integer.class },
				"pay:payconfig:enable");
		assertPermission("disable", new Class[] { String.class, Integer.class }, "pay:payconfig:enable");
		assertPermission("check", new Class[] { String.class, String.class }, "pay:payconfig:check");
		assertPermission("migrateLegacy", new Class[] {}, "pay:payconfig:migrate");
	}

	@Test
	void legacyCertificatePathUploadIsDisabled() {
		PayConfigController controller = controller();
		MockMultipartFile file = new MockMultipartFile("file", "cert.pem", "application/x-pem-file",
				"content".getBytes());

		Result<?> result = controller.uploadFile(file);

		assertEquals(1, result.getCode());
		assertTrue(result.getMsg().contains("已停用"));
		assertNotNull(result.getMsg());
	}

	@Test
	void safeConfigViewHasNoSecretOrCipherFields() {
		Set<String> fields = Arrays.stream(PayConfigVO.class.getDeclaredFields())
			.map(Field::getName)
			.collect(Collectors.toSet());

		assertFalse(fields.contains("mchKey"));
		assertFalse(fields.contains("apiv3Key"));
		assertFalse(fields.contains("privateKeyPath"));
		assertFalse(fields.contains("privateCertPath"));
		assertFalse(fields.contains("publicKeyPath"));
		assertFalse(fields.contains("keyPath"));
		assertFalse(fields.contains("ciphertext"));
		assertFalse(fields.contains("nonce"));
	}

	private void assertPermission(String methodName, Class<?>[] parameterTypes, String permission)
			throws NoSuchMethodException {
		Method method = PayConfigController.class.getDeclaredMethod(methodName, parameterTypes);
		SaCheckPermission annotation = method.getAnnotation(SaCheckPermission.class);
		assertNotNull(annotation, methodName + " 缺少权限注解");
		assertArrayEquals(new String[] { permission }, annotation.value());
	}

	private PayConfigController controller() {
		return new PayConfigController(mock(IPayConfigService.class), mock(PayCredentialService.class),
				mock(PayConfigRevisionService.class), mock(PayConfigCheckService.class),
				mock(PayLegacyMigrationService.class));
	}

}
