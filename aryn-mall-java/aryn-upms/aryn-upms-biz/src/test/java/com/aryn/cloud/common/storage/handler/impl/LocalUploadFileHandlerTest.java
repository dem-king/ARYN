package com.aryn.cloud.common.storage.handler.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.storage.entity.StorageConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LocalUploadFileHandlerTest {

	@TempDir
	Path uploadRoot;

	@AfterEach
	void clearTenant() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void writesToTenantDirectoryAndReturnsCloudRelativeUrl() throws Exception {
		ArynTenantContextHolder.setTenantId("1881232176465358849");
		StorageConfig config = new StorageConfig();
		config.setBucket(uploadRoot.toString());
		byte[] content = "image-content".getBytes();
		LocalUploadFileHandler handler = new LocalUploadFileHandler(new MockEnvironment());

		String url = handler.doUploadFile(config, new ByteArrayInputStream(content), "avatar.png", content.length);

		assertThat(url).startsWith("/upms/file/local/1881232176465358849/").endsWith(".png");
		Path tenantDirectory = uploadRoot.resolve("1881232176465358849");
		try (var files = Files.list(tenantDirectory)) {
			var uploadedFiles = files.toList();
			assertThat(uploadedFiles).hasSize(1);
			Path uploadedFile = uploadedFiles.get(0);
			assertThat(Files.readAllBytes(uploadedFile)).isEqualTo(content);
		}
	}

	@Test
	void usesBootPrefixAndConfiguredDomain() {
		MockEnvironment bootEnvironment = new MockEnvironment().withProperty("hx.cloud.enable", "false");
		LocalUploadFileHandler handler = new LocalUploadFileHandler(bootEnvironment);

		assertThat(handler.resolvePublicBaseUrl(null)).isEqualTo("/boot");
		assertThat(handler.resolvePublicBaseUrl("https://files.example.com/")).isEqualTo("https://files.example.com");
	}

}
