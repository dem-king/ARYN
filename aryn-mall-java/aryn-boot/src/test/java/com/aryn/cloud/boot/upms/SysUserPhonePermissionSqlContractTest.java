package com.aryn.cloud.boot.upms;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 员工账号手机号修改权限种子契约。
 *
 * <p>改手机号与查看完整手机号是两类权限：查看用 {@code upms:sysuser:phone}，
 * 修改用独立的 {@code upms:sysuser:phone:edit}，避免「只能看号码」的账号顺带获得
 * 换绑（等价于接管账号）的能力。种子按「重置密码」同批角色授权。</p>
 */
class SysUserPhonePermissionSqlContractTest {

	private static final String MENU_ID = "2110000000000000207";

	private static final String PERMISSION = "upms:sysuser:phone:edit";

	private static final String PARENT_ID = "1491752531735490561";

	private final Path projectRoot = findProjectRoot();

	@Test
	void bootAndCloudScriptsDeclareIndependentPhoneEditPermission() throws IOException {
		Path bootPath = projectRoot.resolve("db/boot/182sysuser_phone_edit_permission.sql");
		Path cloudPath = projectRoot.resolve("db/cloud/184sysuser_phone_edit_permission.sql");
		assertThat(Files.exists(bootPath)).isTrue();
		assertThat(Files.exists(cloudPath)).isTrue();

		String boot = Files.readString(bootPath);
		String cloud = Files.readString(cloudPath);

		for (String sql : List.of(boot, cloud)) {
			assertThat(sql).contains("'" + MENU_ID + "'", "'" + PARENT_ID + "'", PERMISSION,
					"INSERT IGNORE INTO `sys_menu`", "INSERT IGNORE INTO `sys_role_menu`",
					"INSERT IGNORE INTO `sys_tenant_menu`", "peer.`permission` = 'upms:sysuser:password'",
					"MD5(CONCAT(", "SET FOREIGN_KEY_CHECKS = 1;");
		}

		String bootBody = withoutComments(boot).replaceFirst("USE `aryn_boot`;", "USE `aryn_upms`;");
		assertThat(bootBody).as("Boot 与 Cloud 手机号修改权限脚本除目标数据库外必须保持一致")
			.isEqualTo(withoutComments(cloud));

		String fullSql = Files.readString(projectRoot.resolve("db/boot/aryn_boot_full.sql"));
		assertThat(fullSql).contains("Source: db/boot/182sysuser_phone_edit_permission.sql",
				"'" + MENU_ID + "'", PERMISSION);
	}

	private static String withoutComments(String sql) {
		return sql.replaceAll("(?m)^\\s*--.*(?:\\R|$)", "").trim();
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
