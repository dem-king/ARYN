package com.aryn.cloud.upms.storage;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageConfigSchemaContractTest {

	private final Path projectRoot = findProjectRoot();

	@Test
	void storageConfigSchemasAndMigrationsDeclarePathStyleColumn() throws IOException {
		for (String relativePath : List.of("db/boot/2aryn_boot.sql", "db/cloud/2aryn_upms.sql")) {
			String sql = Files.readString(projectRoot.resolve(relativePath));
			assertTrue(sql.contains("`style_access_enabled`"),
					() -> relativePath + " must define style_access_enabled");
			assertTrue(sql.contains("`domain`"), () -> relativePath + " must define domain");
			assertTrue(sql.contains("`endpoint` varchar(255)"), () -> relativePath + " must support long endpoints");
			assertTrue(sql.contains("`bucket` varchar(255)"), () -> relativePath + " must support long paths");
		}

		for (String relativePath : List.of("db/boot/23storage_config_lengths.sql",
				"db/cloud/23storage_config_lengths.sql")) {
			String sql = Files.readString(projectRoot.resolve(relativePath));
			assertTrue(sql.contains("endpoint varchar(255)"));
			assertTrue(sql.contains("bucket varchar(255)"));
			assertTrue(sql.contains("information_schema.tables"));
		}

		for (String relativePath : List.of("db/boot/21storage_config_schema.sql",
				"db/cloud/21storage_config_schema.sql")) {
			String sql = Files.readString(projectRoot.resolve(relativePath));
			assertTrue(sql.contains("information_schema.columns"),
					() -> relativePath + " must be repeatable");
			assertTrue(sql.contains("ADD COLUMN style_access_enabled"),
					() -> relativePath + " must add style_access_enabled");
			assertTrue(sql.contains("ADD COLUMN domain"), () -> relativePath + " must add domain");
		}

		for (String relativePath : List.of("db/boot/22storage_type_unification.sql",
				"db/cloud/22storage_type_unification.sql")) {
			String sql = Files.readString(projectRoot.resolve(relativePath));
			for (String type : List.of("local", "aliyun", "qiniu", "tencent", "minio")) {
				assertTrue(sql.contains("''" + type + "''"), () -> relativePath + " must declare " + type);
			}
			assertTrue(sql.contains("information_schema.tables"), () -> relativePath + " must be repeatable");
		}

		String gatewayMigration = Files.readString(projectRoot.resolve("db/cloud/4gateway_local_file_ignore.sql"));
		assertTrue(gatewayMigration.contains("/upms/file/local/**"));
		assertTrue(gatewayMigration.contains("MD5(@gateway_content)"));
	}

	private static Path findProjectRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null && !Files.isDirectory(current.resolve("db/cloud"))) {
			current = current.getParent();
		}
		assertNotNull(current, "Cannot locate aryn-mall-java project root");
		return current;
	}

}
