package com.aryn.cloud.upms.support;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryEvidenceUploadTest {

	private final UploadFileValidator validator = new UploadFileValidator();

	@Test
	void deliveryEvidenceAcceptsOnlyRealJpegAndPng() {
		byte[] jpeg = new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x01 };
		byte[] png = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };

		assertThatCode(() -> validator.validateDeliveryEvidence(
			new MockMultipartFile("file", "evidence.jpg", "image/jpeg", jpeg))).doesNotThrowAnyException();
		assertThatCode(() -> validator.validateDeliveryEvidence(
			new MockMultipartFile("file", "evidence.png", "image/png", png))).doesNotThrowAnyException();
	}

	@Test
	void deliveryEvidenceRejectsDisguisedAndUnsupportedImages() {
		assertRejected("attack.png", "image/png", "<html>attack</html>".getBytes());
		assertRejected("image.gif", "image/gif", "GIF89a".getBytes());
		assertRejected("image.bmp", "image/bmp", "BMpayload".getBytes());
		assertRejected("image.webp", "image/webp", "RIFFxxxxWEBP".getBytes());
	}

	@Test
	void staffUploadEndpointRequiresTobDeliveryPermission() throws Exception {
		String source = Files.readString(Path.of(
			"src/main/java/com/aryn/cloud/upms/controller/StaffDeliveryEvidenceController.java"));

		assertThat(source).contains("SecurityUtils.requireUser(DeviceTypeEnum.TOB)");
		assertThat(source).contains("@SaCheckPermission(\"order:delivery:execute\")");
		assertThat(source).contains("uploadObject(");
	}

	private void assertRejected(String name, String contentType, byte[] content) {
		assertThatThrownBy(() -> validator.validateDeliveryEvidence(
			new MockMultipartFile("file", name, contentType, content)))
			.isInstanceOf(ArynBusinessException.class);
	}

}
