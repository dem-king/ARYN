package com.aryn.cloud.boot.order;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 「导入清单下单」双模式 SQL 契约。
 *
 * <p>Cloud 基线（7aryn_order.sql）用于新环境一次装库，增量脚本（220）用于存量库升级：
 * 两者缺一都会出现「老库能跑、新库报表不存在」或反向的漂移。这里同时守住
 * 基线包含两张表、增量可重复执行、Nacos 白名单已登记。
 */
class ReplenishDirectImportSqlContractTest {

	private static final String BOOT_INCREMENTAL = "db/boot/213replenish_direct_import_incremental.sql";

	private static final String CLOUD_INCREMENTAL = "db/cloud/220replenish_direct_import_incremental.sql";

	private static final String CLOUD_BASELINE = "db/cloud/7aryn_order.sql";

	private static final String CLOUD_NACOS_INCREMENTAL = "db/cloud/221replenish_direct_import_nacos_tenant_tables.sql";

	private final Path projectRoot = findProjectRoot();

	@Test
	void bothIncrementalScriptsCreateTheSameTwoTables() throws IOException {
		for (String path : new String[] { BOOT_INCREMENTAL, CLOUD_INCREMENTAL }) {
			String sql = read(path);
			assertThat(sql).as(path).contains("CREATE TABLE IF NOT EXISTS `replenish_direct_import`");
			assertThat(sql).as(path).contains("CREATE TABLE IF NOT EXISTS `replenish_direct_import_row`");
			assertThat(sql).as(path).contains("UNIQUE KEY `uk_replenish_direct_import_row`");
			assertThat(sql).as(path).contains("`planned_quantity`");
			// 注释里会写「禁止 DROP/TRUNCATE」，只能断言可执行语句
			String executable = sql.lines()
				.filter(line -> !line.trim().startsWith("--"))
				.reduce("", (a, b) -> a + "\n" + b);
			assertThat(executable).as(path).doesNotContain("DROP TABLE").doesNotContain("TRUNCATE");
		}
	}

	@Test
	void cloudBaselineShipsBothTablesForFreshInstall() throws IOException {
		String sql = read(CLOUD_BASELINE);
		assertThat(sql).contains("CREATE TABLE `replenish_direct_import`");
		assertThat(sql).contains("CREATE TABLE `replenish_direct_import_row`");
		assertThat(sql).contains("`operator_user_id`");
		assertThat(sql).contains("`resolved_action`");
		assertThat(sql).contains("UNIQUE KEY `uk_replenish_direct_import_row` (`tenant_id`, `import_id`, `row_no`)");
	}

	@Test
	void cloudNacosWhitelistRegistersBothTables() throws IOException {
		assertThat(read(CLOUD_NACOS_INCREMENTAL))
			.contains("replenish_direct_import")
			.contains("replenish_direct_import_row");
	}

	private String read(String relativePath) throws IOException {
		return Files.readString(projectRoot.resolve(relativePath));
	}

	private static Path findProjectRoot() {
		Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
		while (current != null && !Files.isDirectory(current.resolve("db/cloud"))) {
			current = current.getParent();
		}
		if (current == null) {
			throw new IllegalStateException("未找到包含 db/cloud 的项目根目录");
		}
		return current;
	}

}
