package com.aryn.cloud.boot.product;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ProductBrandSqlContractTest {

	private static final String TABLE_COLLATION =
			"DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci";

	private static final String EXISTING_TABLE_CONVERSION =
			"ALTER TABLE goods_brand\n  CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;";

	private static final String BRAND_TABLE_START = "CREATE TABLE IF NOT EXISTS goods_brand (";

	private static final String BRAND_TABLE_END = "COMMENT='商品品牌';";

	private final Path projectRoot = findProjectRoot();

	@Test
	void brandSchemasKeepJoinColumnsOnTheProjectCollation() throws IOException {
		String boot = Files.readString(projectRoot.resolve("db/boot/19product_brand.sql"));
		String cloud = Files.readString(projectRoot.resolve("db/cloud/19product_brand.sql"));
		String full = Files.readString(projectRoot.resolve("db/boot/aryn_boot_full.sql"));

		assertBrandSchema(boot);
		assertBrandSchema(cloud);
		assertBrandSchema(full);
	}

	private static void assertBrandSchema(String sql) {
		String normalized = sql.replace("\r\n", "\n").replace('\r', '\n');
		assertThat(brandTableDefinition(normalized)).contains(TABLE_COLLATION);
		assertThat(normalized).contains(EXISTING_TABLE_CONVERSION, "ADD COLUMN brand_id");
	}

	private static String brandTableDefinition(String sql) {
		int start = sql.indexOf(BRAND_TABLE_START);
		int end = sql.indexOf(BRAND_TABLE_END, start);
		assertThat(start).as("商品品牌建表起点").isGreaterThanOrEqualTo(0);
		assertThat(end).as("商品品牌建表终点").isGreaterThanOrEqualTo(start);
		return sql.substring(start, end + BRAND_TABLE_END.length());
	}

	private static Path findProjectRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null && !Files.isDirectory(current.resolve("db/cloud"))) {
			current = current.getParent();
		}
		if (current == null) {
			throw new IllegalStateException("无法定位 aryn-mall-java 根目录");
		}
		return current;
	}

}
