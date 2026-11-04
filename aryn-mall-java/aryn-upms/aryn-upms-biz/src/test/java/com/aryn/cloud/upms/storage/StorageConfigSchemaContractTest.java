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

	/**
	 * 平台管理员（系统租户）同样有头像等平台级上传：没有启用的文件存储会让个人信息页
	 * 更换头像直接报「当前租户未配置启用的文件存储」。种子必须双模式齐全、幂等，
	 * 并已合并进 Boot 全量脚本。
	 */
	@Test
	void platformTenantHasDefaultStorageConfigSeed() throws IOException {
		for (String relativePath : List.of("db/boot/181platform_storage_config.sql",
				"db/cloud/183platform_storage_config.sql")) {
			String sql = Files.readString(projectRoot.resolve(relativePath));
			assertTrue(sql.contains("'1881232176465358849'"),
					() -> relativePath + " must target the platform tenant");
			assertTrue(sql.contains("'1881232176465358849001'"),
					() -> relativePath + " must use a fixed config id for idempotency");
			assertTrue(sql.contains("'/data/aryn/uploads'"),
					() -> relativePath + " must use the docker local storage root");
			assertTrue(sql.contains("NOT EXISTS") && sql.contains("`del_flag` = '0'")
					&& sql.contains("`status` = '0'"),
					() -> relativePath + " must skip when an enabled config already exists");
		}

		String fullSql = Files.readString(projectRoot.resolve("db/boot/aryn_boot_full.sql"));
		assertTrue(fullSql.contains("Source: db/boot/181platform_storage_config.sql"),
				"aryn_boot_full.sql must include the platform storage seed");
		assertTrue(fullSql.contains("'1881232176465358849001'"),
				"aryn_boot_full.sql must contain the platform storage row");
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
