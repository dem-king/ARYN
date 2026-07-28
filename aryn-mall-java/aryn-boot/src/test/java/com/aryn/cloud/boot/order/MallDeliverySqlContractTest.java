package com.aryn.cloud.boot.order;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MallDeliverySqlContractTest {

	private static final List<String> TABLES = List.of(
			"order_delivery_task",
			"order_delivery_task_item",
			"order_delivery_evidence",
			"order_delivery_task_log",
			"order_delivery_area",
			"sys_user_wechat_binding",
			"message_channel_task");

	private final Path projectRoot = findProjectRoot();

	@Test
	void bootAndCloudSchemasContainDeliveryTablesAndCriticalIndexes() throws IOException {
		String boot = Files.readString(projectRoot.resolve("db/boot/21mall_delivery.sql"));
		String cloud = readCloudSql();

		for (String table : TABLES) {
			assertTableContract(boot, table);
			assertTableContract(cloud, table);
		}

		assertThat(boot).contains(
				"UNIQUE KEY `uk_delivery_task_order` (`tenant_id`, `order_id`)",
				"UNIQUE KEY `uk_delivery_task_no` (`tenant_id`, `task_no`)",
				"UNIQUE KEY `uk_delivery_task_item` (`tenant_id`, `task_id`, `order_item_id`)",
				"`attempt_no` int NOT NULL",
				"`version` int NOT NULL",
				"`delivered_at` datetime DEFAULT NULL");
		assertThat(cloud).contains(
				"UNIQUE KEY `uk_delivery_task_order` (`tenant_id`, `order_id`)",
				"UNIQUE KEY `uk_delivery_task_no` (`tenant_id`, `task_no`)",
				"UNIQUE KEY `uk_delivery_task_item` (`tenant_id`, `task_id`, `order_item_id`)",
				"`attempt_no` int NOT NULL",
				"`version` int NOT NULL",
				"`delivered_at` datetime DEFAULT NULL");
	}

	@Test
	void deliveryEvidenceAndMaterialSchemaNeverPersistClientUrls() throws IOException {
		String boot = Files.readString(projectRoot.resolve("db/boot/21mall_delivery.sql"));
		String cloud = readCloudSql();

		assertThat(boot).contains("`material_id` varchar(32) NOT NULL", "`binding_status` varchar(16) NOT NULL")
				.doesNotContain("`evidence_url`");
		assertThat(cloud).contains("`material_id` varchar(32) NOT NULL", "`binding_status` varchar(16) NOT NULL")
				.doesNotContain("`evidence_url`");
		assertThat(boot).contains("ALTER TABLE `sys_material`", "`object_key`", "`reservation_id`");
		assertThat(cloud).contains("ALTER TABLE `sys_material`", "`object_key`", "`reservation_id`");
	}

	private String readCloudSql() throws IOException {
		return Files.readString(projectRoot.resolve("db/cloud/21order_delivery_task.sql"))
				+ Files.readString(projectRoot.resolve("db/cloud/21delivery_staff_wechat.sql"))
				+ Files.readString(projectRoot.resolve("db/cloud/21message_channel_task.sql"));
	}

	private static void assertTableContract(String sql, String table) {
		String definition = tableDefinition(sql, table);
		assertThat(definition).as(table).contains("`tenant_id`", "`del_flag`");
	}

	private static String tableDefinition(String sql, String table) {
		String marker = "CREATE TABLE `" + table + "`";
		int start = sql.indexOf(marker);
		assertThat(start).as("缺少表 " + table).isGreaterThanOrEqualTo(0);
		int end = sql.indexOf(";", start);
		assertThat(end).as("表定义缺少结束符 " + table).isGreaterThan(start);
		return sql.substring(start, end);
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
