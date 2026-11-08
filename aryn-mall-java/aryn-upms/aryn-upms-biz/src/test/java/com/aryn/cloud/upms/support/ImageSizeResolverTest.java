package com.aryn.cloud.upms.support;

import com.aryn.cloud.common.core.dto.SysStorageConfigDTO;
import com.aryn.cloud.upms.api.vo.RemoteImageSizeVO;
import com.aryn.cloud.upms.service.ISysStorageConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImageSizeResolverTest {

	private static final String FILE_NAME = "00000000-0000-0000-0000-000000000001.png";

	@TempDir
	Path tempDir;

	@Test
	void readsSizeFromLocalUploadWithoutDecodingWholeImage() throws Exception {
		Path tenantDirectory = Files.createDirectories(tempDir.resolve("123"));
		BufferedImage image = new BufferedImage(40, 30, BufferedImage.TYPE_INT_RGB);
		ImageIO.write(image, "png", tenantDirectory.resolve(FILE_NAME).toFile());

		ISysStorageConfigService storageConfigService = mock(ISysStorageConfigService.class);
		when(storageConfigService.getLocalStorageRoots()).thenReturn(List.of(tempDir.toString()));
		when(storageConfigService.getConfig()).thenReturn(null);
		ImageSizeResolver resolver = new ImageSizeResolver(storageConfigService, new LocalFilePathResolver());

		Map<String, RemoteImageSizeVO> result = resolver
			.resolve(List.of("/boot/file/local/123/" + FILE_NAME));

		assertThat(result).containsKey("/boot/file/local/123/" + FILE_NAME);
		assertThat(result.get("/boot/file/local/123/" + FILE_NAME).getWidth()).isEqualTo(40);
		assertThat(result.get("/boot/file/local/123/" + FILE_NAME).getHeight()).isEqualTo(30);
	}

	@Test
	void returnsEmptyForUntrustedOrMissingImages() {
		ISysStorageConfigService storageConfigService = mock(ISysStorageConfigService.class);
		when(storageConfigService.getLocalStorageRoots()).thenReturn(List.of());
		SysStorageConfigDTO config = new SysStorageConfigDTO();
		config.setType("local");
		config.setDomain("https://cdn.example.com");
		when(storageConfigService.getConfig()).thenReturn(config);
		ImageSizeResolver resolver = new ImageSizeResolver(storageConfigService, new LocalFilePathResolver());

		// 非存储域名：不做远程请求（避免 SSRF），直接返回空
		assertThat(resolver.resolve(List.of("https://evil.example.com/a.png"))).isEmpty();
		// 本地文件不存在：返回空，由前端 1:1 兜底
		assertThat(resolver.resolve(List.of("/file/local/123/" + FILE_NAME))).isEmpty();
	}

	@Test
	void ignoresBlankUrls() {
		ISysStorageConfigService storageConfigService = mock(ISysStorageConfigService.class);
		ImageSizeResolver resolver = new ImageSizeResolver(storageConfigService, new LocalFilePathResolver());

		assertThat(resolver.resolve(List.of("", "  "))).isEmpty();
		assertThat(resolver.resolve(null)).isEmpty();
	}

}
