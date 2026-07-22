package com.aryn.cloud.upms.support;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFilePathResolverTest {

	private final LocalFilePathResolver resolver = new LocalFilePathResolver();

	@TempDir
	Path root;

	@Test
	void resolvesGeneratedFileInsideTenantDirectory() throws Exception {
		Path tenantDirectory = Files.createDirectories(root.resolve("1590229800633634816"));
		Path image = Files.write(tenantDirectory.resolve("c3e7b91e-e58b-4942-b640-f5905db2394b.jpeg"),
				new byte[] { 1 });

		assertThat(resolver.resolve(root.toString(), "1590229800633634816",
				"c3e7b91e-e58b-4942-b640-f5905db2394b.jpeg")).isEqualTo(image.toRealPath());
	}

	@Test
	void rejectsTenantPathTraversal() {
		assertThatThrownBy(() -> resolver.resolve(root.toString(), "..", "secret.jpeg"))
			.isInstanceOf(ArynBusinessException.class)
			.isInstanceOfSatisfying(ArynBusinessException.class,
					exception -> assertThat(exception.getMsg()).isEqualTo("非法租户标识"));
	}

	@Test
	void rejectsFilePathTraversal() {
		assertThatThrownBy(() -> resolver.resolve(root.toString(), "1590229800633634816", "../secret.jpeg"))
			.isInstanceOf(ArynBusinessException.class)
			.isInstanceOfSatisfying(ArynBusinessException.class,
					exception -> assertThat(exception.getMsg()).isEqualTo("非法文件路径"));
	}

	@Test
	void rejectsNonGeneratedFileName() {
		assertThatThrownBy(() -> resolver.resolve(root.toString(), "1590229800633634816", "manual.jpeg"))
			.isInstanceOf(ArynBusinessException.class)
			.isInstanceOfSatisfying(ArynBusinessException.class,
					exception -> assertThat(exception.getMsg()).isEqualTo("非法文件路径"));
	}

}
