package com.aryn.cloud.upms.menu;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 分销管理与销售管理独立为一级菜单的契约。
 *
 * <p>两者是「渠道与人 + 资金台账」，与营销活动/优惠/货盘不属于同一类事务，且含财务敏感数据；
 * 挂在「营销管理」下会导致授予任一页面都必须连带授予营销管理祖先目录。本契约防止后续
 * 种子或增量脚本把 parent_id 改回营销管理，也防止 boot/cloud 双模式脚本漂移。</p>
 *
 * <p>另一条硬约束：「分销管理」的 redirect 必须为空——它原指向 /promotion/distribution/config，
 * 而基础版套餐（app_base）不含 app_market 分销配置页，目录点击会落 404；置空后由前端
 * 按第一个可见子页面自动生成 redirect。</p>
 */
class SalesDistributionMenuRelocationContractTest {

	private static final String BOOT_SCRIPT = "db/boot/222relocate_sales_distribution_menus.sql";

	private static final String CLOUD_SCRIPT = "db/cloud/233relocate_sales_distribution_menus.sql";

	private static final String MARKETING_MENU_ID = "1779386604402573314";

	private final Path projectRoot = findProjectRoot();

	@Test
	void bothModesPromoteMenusToTopLevel() throws IOException {
		for (String relativePath : List.of(BOOT_SCRIPT, CLOUD_SCRIPT)) {
			String sql = Files.readString(projectRoot.resolve(relativePath)).replaceAll("\\s+", " ");
			assertTrue(sql.contains(
					"SET `parent_id` = '0', `sort` = 52, `redirect` = NULL"),
				() -> relativePath + " 必须把分销管理升为一级菜单并清空 redirect");
			assertTrue(sql.contains("SET `parent_id` = '0', `sort` = 54"),
				() -> relativePath + " 必须把销售管理升为一级菜单");
			assertTrue(sql.contains("`id` = '1991000000000000001'"),
				() -> relativePath + " 必须按固定 ID 定位分销管理");
			assertTrue(sql.contains("`id` = '2130000000000000100'"),
				() -> relativePath + " 必须按固定 ID 定位销售管理");
			// 幂等：UPDATE 带旧父目录条件，重跑不会把已迁移的行再次改写
			assertTrue(sql.contains("AND `parent_id` = '" + MARKETING_MENU_ID + "'"),
				() -> relativePath + " 的 UPDATE 必须带旧父目录条件以保证幂等");
			assertTrue(sql.contains("INSERT IGNORE INTO `sys_role_menu`"),
				() -> relativePath + " 必须补偿角色目录授权");
			assertTrue(sql.contains("INSERT IGNORE INTO `sys_tenant_menu`"),
				() -> relativePath + " 必须补偿租户目录授权");
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
		assertTrue(fullSql.contains("Source: db/boot/" + BOOT_SCRIPT.substring("db/boot/".length())),
				"全量 SQL 必须包含分销/销售菜单独立段");
		assertTrue(fullSql.contains("SET `parent_id` = '0', `sort` = 52, `redirect` = NULL"));
		assertTrue(fullSql.contains("SET `parent_id` = '0', `sort` = 54"));
	}

	/**
	 * 基线种子仍把两者写在营销管理下——增量脚本负责搬移，基线不应被顺手改写
	 * （改写会让全新库与存量库走两条不同路径，增量脚本反而变成空操作）。
	 */
	@Test
	void baselineSeedsStillNestUnderMarketing() throws IOException {
		for (String relativePath : List.of("db/boot/2aryn_boot.sql", "db/cloud/2aryn_upms.sql")) {
			String sql = Files.readString(projectRoot.resolve(relativePath));
			assertTrue(sql.contains("'1991000000000000001', '分销管理'"),
				() -> relativePath + " 应保留分销管理基线行");
			assertFalse(sql.contains("'1991000000000000001', '分销管理', NULL, '/promotion/distribution', NULL, '0'"),
				() -> relativePath + " 基线不应直接改成一级菜单（改由增量脚本迁移）");
		}
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
			.collect(java.util.stream.Collectors.joining("\n"))
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
