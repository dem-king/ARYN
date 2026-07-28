package com.aryn.cloud.boot.order;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MallDeliveryMenuSqlContractTest {

	private final Path projectRoot = findProjectRoot();

	@Test
	void bootAndCloudMenusExposeDeliveryRoutesPermissionsAndDictionary() throws IOException {
		String boot = Files.readString(projectRoot.resolve("db/boot/21mall_delivery_menu.sql"));
		String cloud = Files.readString(projectRoot.resolve("db/cloud/21mall_delivery_menu.sql"));

		assertThat(boot).startsWith("USE aryn_boot;");
		assertThat(cloud).startsWith("USE aryn_upms;");
		assertThat(boot.replaceFirst("USE aryn_boot;", "USE aryn_upms;"))
				.as("Boot 与 Cloud 配送菜单除目标数据库外必须保持一致")
				.isEqualTo(cloud);

		assertThat(boot).contains("'商城配送'", "'/order/delivery-task'", "'order/delivery-task/index'",
				"'/order/delivery-area'", "'order/delivery-area/index'", "'商城配送'", "'3'", "'delivery_way'");
		List<String> permissions = List.of("order:delivery:page", "order:delivery:get", "order:delivery:assign",
				"order:delivery:reassign", "order:delivery:exception", "order:delivery:return",
				"order:delivery:area", "order:delivery:execute");
		assertThat(boot).contains(permissions.toArray(String[]::new));
		assertThat(boot).contains("INSERT INTO sys_role_menu", "role.role_code = 'ROLE_ADMIN'",
				"INSERT INTO sys_tenant_menu", "tenant.id <> '1881232176465358849'", "NOT EXISTS");
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
