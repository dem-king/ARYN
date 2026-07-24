package com.aryn.cloud.pay.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.pay.service.IPayConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PayConfigControllerTest {

	@TempDir
	Path certificateDirectory;

	@Test
	void certificateUploadUsesPaymentEditPermission() throws NoSuchMethodException {
		Method method = PayConfigController.class.getDeclaredMethod("uploadFile",
				org.springframework.web.multipart.MultipartFile.class);
		SaCheckPermission permission = method.getAnnotation(SaCheckPermission.class);
		assertNotNull(permission);
		assertArrayEquals(new String[] { "pay:payconfig:edit" }, permission.value());
	}

	@Test
	void certificateUploadIgnoresTraversalFilenameAndUsesGeneratedName() throws Exception {
		PayConfigController controller = controller();
		MockMultipartFile file = new MockMultipartFile("file", "../../outside.pem", "application/x-pem-file",
				"certificate".getBytes());

		Result<?> result = controller.uploadFile(file);

		assertEquals(0, result.getCode());
		Path stored = Path.of((String) result.getData()).normalize();
		assertTrue(stored.startsWith(certificateDirectory.toAbsolutePath().normalize()));
		assertFalse(stored.getFileName().toString().contains("outside"));
		assertTrue(stored.getFileName().toString().endsWith(".pem"));
	}

	@Test
	void certificateUploadRejectsUnexpectedFileTypes() throws Exception {
		PayConfigController controller = controller();
		MockMultipartFile file = new MockMultipartFile("file", "shell.jsp", "text/plain", "payload".getBytes());

		Result<?> result = controller.uploadFile(file);

		assertEquals(1, result.getCode());
		assertEquals("仅支持 pem、crt、cer、p12 证书文件", result.getMsg());
	}

	private PayConfigController controller() {
		PayConfigController controller = new PayConfigController(mock(IPayConfigService.class));
		ReflectionTestUtils.setField(controller, "certDir", certificateDirectory.toString());
		return controller;
	}

}
