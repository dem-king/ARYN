package com.aryn.cloud.upms.support;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class MultipartUploadConfigurationTest {

	@Test
	void shouldAllowFilesAcceptedByUploadValidator() throws Exception {
		String application = Files.readString(Path.of("src/main/resources/application.yml"));

		assertThat(application).contains("max-file-size: 10MB", "max-request-size: 11MB");
	}

}
