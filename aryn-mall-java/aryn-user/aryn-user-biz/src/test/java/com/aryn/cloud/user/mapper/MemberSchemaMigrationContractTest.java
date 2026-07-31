package com.aryn.cloud.user.mapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemberSchemaMigrationContractTest {

	private final Path projectRoot = findProjectRoot();

	@Test
	void schemasAndRepairMigrationsDeclareTotalPoint() throws IOException {
		for (String relativePath : List.of("db/boot/4aryn_boot_member.sql", "db/cloud/4aryn_user.sql")) {
			String sql = Files.readString(projectRoot.resolve(relativePath));
			assertTrue(sql.contains("ADD COLUMN `total_point`"),
					() -> relativePath + " must initialize total_point");
		}

		for (String relativePath : List.of("db/boot/25member_total_point_repair.sql",
				"db/cloud/25member_total_point_repair.sql")) {
			String sql = Files.readString(projectRoot.resolve(relativePath));
			assertTrue(sql.contains("information_schema.columns"),
					() -> relativePath + " must be repeatable");
			assertTrue(sql.contains("ADD COLUMN total_point"),
					() -> relativePath + " must repair total_point");
			assertTrue(sql.contains("WHERE @aryn_total_point_exists = 0"),
					() -> relativePath + " must not rewrite existing cumulative points");
		}
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
