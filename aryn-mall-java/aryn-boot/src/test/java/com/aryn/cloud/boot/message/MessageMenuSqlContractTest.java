package com.aryn.cloud.boot.message;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageMenuSqlContractTest {

	private final Path projectRoot = findProjectRoot();

	@Test
	void bootAndCloudMenusExposeAllImplementedRoutesAndPermissions() throws IOException {
		String boot = Files.readString(projectRoot.resolve("db/boot/20message_menu.sql"));
		String cloud = Files.readString(projectRoot.resolve("db/cloud/20message_menu.sql"));

		assertThat(boot).startsWith("USE aryn_boot;");
		assertThat(cloud).startsWith("USE aryn_upms;");
		assertThat(boot.replaceFirst("USE aryn_boot;", "USE aryn_upms;"))
				.as("Boot 与 Cloud 菜单种子除目标数据库外必须保持一致")
				.isEqualTo(cloud);

		List<String> routeTokens = List.of(
				"'/message'", "'/message/inbox'", "'message/inbox/index'",
				"'/message/notice'", "'message/notice/index'",
				"'/message/service'", "'message/service/index'",
				"'/message/direct'", "'message/direct/index'");
		List<String> permissionTokens = List.of(
				"'message:notice:page'", "'message:notice:get'", "'message:notice:add'",
				"'message:notice:edit'", "'message:notice:publish'", "'message:notice:revoke'",
				"'message:service:agent'", "'message:service:supervisor'",
				"'message:conversation:initiate'", "'message:staff:direct'");

		assertThat(boot).contains(routeTokens.toArray(String[]::new));
		assertThat(boot).contains(permissionTokens.toArray(String[]::new));
		assertThat(boot).contains("INSERT IGNORE INTO sys_menu", "'app_base'",
				"CROSS JOIN tmp_message_common_menu", "role.role_code = 'ROLE_ADMIN'",
				"INSERT INTO sys_tenant_menu", "tenant.id <> '1881232176465358849'");
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
