package com.aryn.cloud.upms.menu;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 船供菜单归属调整契约：船供活动归营销管理、共享购物车归订单管理。
 *
 * <p>这两个页面分别属于营销域和订单域，菜单路径/组件早已是
 * /promotion/ship-supply 与 /order/shared-cart；本契约防止后续种子或增量脚本
 * 把 parent_id 改回「船供运营」，也防止 boot/cloud 双模式脚本漂移。</p>
 */
class MenuRelocationContractTest {

	private static final String BOOT_SCRIPT = "db/boot/195relocate_ship_supply_menus.sql";

	private static final String CLOUD_SCRIPT = "db/cloud/200relocate_ship_supply_menus.sql";

	private final Path projectRoot = findProjectRoot();

	@Test
	void bothModesRelocateMenusUnderDomainDirectories() throws IOException {
		for (String relativePath : List.of(BOOT_SCRIPT, CLOUD_SCRIPT)) {
			String sql = Files.readString(projectRoot.resolve(relativePath)).replaceAll("\\s+", " ");
			assertTrue(sql.contains(
					"UPDATE `sys_menu` SET `parent_id` = '1779386604402573314', `sort` = 2"),
				() -> relativePath + " 必须把船供活动挂到营销管理");
			assertTrue(sql.contains(
					"UPDATE `sys_menu` SET `parent_id` = '1521496866882236418', `sort` = 15"),
				() -> relativePath + " 必须把共享购物车挂到订单管理");
			assertTrue(sql.contains("'1585191589231144962'"),
				() -> relativePath + " 必须纠正优惠券管理的应用系统");
			assertTrue(sql.contains("INSERT IGNORE INTO `sys_role_menu`"),
				() -> relativePath + " 必须补偿角色父目录授权");
			assertTrue(sql.contains("INSERT IGNORE INTO `sys_tenant_menu`"),
				() -> relativePath + " 必须补偿租户父目录授权");
			assertTrue(sql.contains("NOT EXISTS"),
				() -> relativePath + " 授权补偿必须可重复执行");
		}
	}

	@Test
	void bootAndCloudScriptsStayInSync() throws IOException {
		assertEquals(semantic(Files.readString(projectRoot.resolve(BOOT_SCRIPT))),
				semantic(Files.readString(projectRoot.resolve(CLOUD_SCRIPT))),
				"boot/cloud 增量脚本语义必须一致");
	}

	@Test
	void fullBootSqlContainsRelocationSection() throws IOException {
		String fullSql = Files.readString(projectRoot.resolve("db/boot/aryn_boot_full.sql"));
		assertTrue(fullSql.contains("Source: db/boot/195relocate_ship_supply_menus.sql"));
		assertTrue(fullSql.contains("SET `parent_id` = '1779386604402573314', `sort` = 2"));
		assertTrue(fullSql.contains("SET `parent_id` = '1521496866882236418', `sort` = 15"));
	}

	/**
	 * 剔除注释与 USE/CREATE DATABASE 等模式差异行后比较剩余语句。
	 */
	private static String semantic(String content) {
		return Arrays.stream(content.split("\n"))
			.filter(line -> {
				String trimmed = line.trim();
				return !trimmed.startsWith("--")
						&& !trimmed.toUpperCase().startsWith("USE ")
						&& !trimmed.toUpperCase().startsWith("CREATE DATABASE");
			})
			.collect(Collectors.joining("\n"))
			.replaceAll("\\s+", " ")
			.trim();
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
