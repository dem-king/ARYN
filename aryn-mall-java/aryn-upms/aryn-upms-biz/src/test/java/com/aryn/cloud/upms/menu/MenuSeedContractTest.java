package com.aryn.cloud.upms.menu;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MenuSeedContractTest {

	private static final List<String> GROUP_BUY_MENU_IDS = List.of("1991000000000000050", "1991000000000000051",
			"1991000000000000052", "1991000000000000053", "1991000000000000054", "1991000000000000055",
			"1991000000000000056", "1991000000000000060", "1991000000000000061");

	private final Path projectRoot = findProjectRoot();

	@Test
	void standaloneMemberMenuSeedsDeclareUtf8ConnectionEncoding() throws IOException {
		for (String relativePath : List.of("db/boot/4aryn_boot_member.sql", "db/cloud/4aryn_user_menu.sql")) {
			String sql = Files.readString(projectRoot.resolve(relativePath));
			assertTrue(sql.contains("SET NAMES utf8mb4;"), () -> relativePath + " must select UTF-8 before inserts");
		}
	}

	@Test
	void groupBuyMenusStayInBootAndCloudParity() throws IOException {
		String boot = Files.readString(projectRoot.resolve("db/boot/2aryn_boot.sql"));
		String cloud = Files.readString(projectRoot.resolve("db/cloud/2aryn_upms.sql"));
		for (String menuId : GROUP_BUY_MENU_IDS) {
			assertTrue(boot.contains(menuId), () -> "Boot seed missing group-buy menu " + menuId);
			assertTrue(cloud.contains(menuId), () -> "Cloud seed missing group-buy menu " + menuId);
		}
	}

	@Test
	void repairMigrationsRestoreNamesAndGrantNewMenus() throws IOException {
		for (String relativePath : List.of("db/boot/15menu_seed_repair.sql", "db/cloud/15menu_seed_repair.sql")) {
			String sql = Files.readString(projectRoot.resolve(relativePath));
			assertTrue(sql.contains("'会员等级'"), () -> relativePath + " must restore member menu names");
			assertTrue(sql.contains("INSERT INTO sys_role_menu"), () -> relativePath + " must grant roles");
			assertTrue(sql.contains("INSERT INTO sys_tenant_menu"), () -> relativePath + " must grant tenants");
			assertTrue(sql.contains("NOT EXISTS"), () -> relativePath + " must be idempotent");
			assertTrue(sql.contains("COLLATE = utf8mb4_general_ci"),
					() -> relativePath + " must match the existing menu ID collation");
		}
	}

	@Test
	void mallDeliveryMenuSeedsGrantPackagesAndExistingTenants() throws IOException {
		for (String relativePath : List.of("db/boot/21mall_delivery_menu.sql", "db/cloud/21mall_delivery_menu.sql")) {
			String sql = Files.readString(projectRoot.resolve(relativePath));
			assertTrue(sql.contains("order:delivery:execute"), () -> relativePath + " must expose staff permission");
			assertTrue(sql.contains("role.role_code = 'ROLE_ADMIN'"), () -> relativePath + " must grant tenant admins");
			assertTrue(sql.contains("INSERT INTO sys_tenant_menu"), () -> relativePath + " must grant tenant packages");
			assertTrue(sql.contains("tenant.id <> '1881232176465358849'"),
					() -> relativePath + " must follow existing platform-tenant exclusion");
			assertTrue(sql.contains("NOT EXISTS"), () -> relativePath + " must remain idempotent");
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
