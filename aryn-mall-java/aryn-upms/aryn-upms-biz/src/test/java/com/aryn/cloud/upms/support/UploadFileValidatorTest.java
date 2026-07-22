package com.aryn.cloud.upms.support;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UploadFileValidatorTest {

	private final UploadFileValidator validator = new UploadFileValidator();

	@Test
	void acceptsPngByContentSignature() {
		byte[] png = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };
		MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", png);

		assertThatCode(() -> validator.validate(file)).doesNotThrowAnyException();
	}

	@Test
	void rejectsEmptyFile() {
		MockMultipartFile file = new MockMultipartFile("file", "image.png", "image/png", new byte[0]);

		assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(ArynBusinessException.class)
			.isInstanceOfSatisfying(ArynBusinessException.class,
					exception -> assertThat(exception.getMsg()).isEqualTo("上传文件不能为空"));
	}

	@Test
	void rejectsFileLargerThanTenMegabytesBeforeReadingContent() throws Exception {
		MultipartFile file = mock(MultipartFile.class);
		when(file.isEmpty()).thenReturn(false);
		when(file.getSize()).thenReturn(10L * 1024 * 1024 + 1);
		when(file.getOriginalFilename()).thenReturn("large.png");
		when(file.getInputStream()).thenReturn(InputStream.nullInputStream());

		assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(ArynBusinessException.class)
			.isInstanceOfSatisfying(ArynBusinessException.class,
					exception -> assertThat(exception.getMsg()).isEqualTo("上传文件不能超过10MB"));
	}

	@Test
	void rejectsHtmlRenamedAsPng() {
		MockMultipartFile file = new MockMultipartFile("file", "attack.png", "image/png",
				"<html><script>alert(1)</script></html>".getBytes());

		assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(ArynBusinessException.class)
			.isInstanceOfSatisfying(ArynBusinessException.class,
					exception -> assertThat(exception.getMsg()).isEqualTo("仅支持 JPG、PNG、GIF、BMP、WEBP 图片"));
	}

	@Test
	void rejectsSvgPayload() {
		MockMultipartFile file = new MockMultipartFile("file", "attack.svg", "image/svg+xml",
				"<svg onload=alert(1)></svg>".getBytes());

		assertThatThrownBy(() -> validator.validate(file)).isInstanceOf(ArynBusinessException.class)
			.isInstanceOfSatisfying(ArynBusinessException.class,
					exception -> assertThat(exception.getMsg()).isEqualTo("仅支持 JPG、PNG、GIF、BMP、WEBP 图片"));
	}

}
