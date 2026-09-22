package com.aryn.cloud.boot.promotion;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 首页补给单卡片装修迁移脚本（78 号）契约测试。
 *
 * <p>该脚本要在**已发布的装修快照**里插入一个组件。装修数据是 JSON，
 * 写坏了不会编译报错、也不会被类型检查发现，只会在运营后台或线上首页悄悄出问题。
 * 这里把四条真实踩过的坑固化下来：
 *
 * <ol>
 *   <li>只插入、不整体重写组件数组（否则连带删掉轮播/商品/秒杀楼层）；</li>
 *   <li>必须同时迁移草稿与已发布快照，且覆盖 v2（根级 components）与
 *       v3（sections[].components）两种历史结构；</li>
 *   <li>JSON_SEARCH 返回的是指向 {@code .type} 的路径，要先裁掉 {@code .type}
 *       才能给 JSON_ARRAY_INSERT 当插入位置；</li>
 *   <li>幂等护栏必须用结构字段 type，不能用 title（运营改了标题就失效）。</li>
 * </ol>
 */
class HomeReplenishCardSqlContractTest {

	private static final String BOOT_PATH = "db/boot/78home_replenish_card_entry.sql";

	private static final String CLOUD_PATH = "db/cloud/78home_replenish_card_entry.sql";

	private static final String FULL_PATH = "db/boot/aryn_boot_full.sql";

	private final Path projectRoot = findProjectRoot();

	@Test
	void bothModesExist() throws IOException {
		for (String path : new String[] { BOOT_PATH, CLOUD_PATH }) {
			assertThat(read(path))
				.as("%s 必须插入补给单卡片", path)
				.contains("'replenish-card'");
		}
	}

	@Test
	void neverRewritesTheWholeComponentsArray() throws IOException {
		for (String path : new String[] { BOOT_PATH, CLOUD_PATH }) {
			String sql = executableLines(read(path));
			// 只允许按路径插入；整体重写会把轮播/商品/秒杀一并抹掉
			assertThat(sql).as("%s 必须用 JSON_ARRAY_INSERT", path).contains("JSON_ARRAY_INSERT");
			assertThat(sql)
				.as("%s 不得整体重写 components 数组", path)
				.doesNotContain("JSON_REPLACE(\n      `page_content`,\n      '$.sections[0].components',");
		}
	}

	/** 迁移语句数：草稿 v2/v3 各一条 + 已发布快照 v2/v3 各一条 */
	private static final int EXPECTED_MIGRATION_STATEMENTS = 4;

	@Test
	void migratesDraftAndPublishedSnapshotInBothSchemaFormats() throws IOException {
		// 必须按**语句条数**校验，不能只判 contains：
		// 删掉任一分支（如 v2）后，其余分支仍含同样的路径与 formData 字样，
		// 文件级 contains 会静默放过——实测注入「删掉 v2 草稿段」时正是如此。
		for (String path : new String[] { BOOT_PATH, CLOUD_PATH }) {
			String sql = executableLines(read(path));

			// 草稿 2 条（v3 + v2）：客户端只读草稿的租户也生效
			assertThat(countOccurrences(sql, "UPDATE `page_design`\nSET `page_content` = JSON_ARRAY_INSERT("))
				.as("%s 草稿迁移应有 v2/v3 两条语句", path)
				.isEqualTo(2);
			// 已发布快照 2 条（v3 + v2）：线上首页读的是快照，只改草稿不生效
			assertThat(countOccurrences(sql, "UPDATE `page_design_version` v"))
				.as("%s 已发布快照迁移应有 v2/v3 两条语句", path)
				.isEqualTo(2);

			// 两条 v3 与两条 v2 的路径形态
			assertThat(countOccurrences(sql, "'$.sections[*].components[*].type'"))
				.as("%s v3 路径应出现在草稿与快照两条语句里", path)
				.isGreaterThanOrEqualTo(2);
			assertThat(countOccurrences(sql, "'$.components[*].type'"))
				.as("%s v2 路径应出现在草稿与快照两条语句里", path)
				.isGreaterThanOrEqualTo(2);

			// 每种格式各 2 份组件定义：v2 用 formData、v3 用 props
			assertThat(countOccurrences(sql, "'formData', JSON_OBJECT("))
				.as("%s v2 的 formData 定义应恰好 2 处", path)
				.isEqualTo(2);
			assertThat(countOccurrences(sql, "'props', JSON_OBJECT("))
				.as("%s v3 的 props 定义应恰好 2 处", path)
				.isEqualTo(2);

			assertThat(countOccurrences(sql, "JSON_ARRAY_INSERT("))
				.as("%s 迁移语句总数", path)
				.isEqualTo(EXPECTED_MIGRATION_STATEMENTS);
		}
	}

	@Test
	void stripsTheTypeSuffixBeforeInserting() throws IOException {
		for (String path : new String[] { BOOT_PATH, CLOUD_PATH }) {
			String sql = read(path);
			// JSON_SEARCH 给的是 ...components[N].type，必须 -5 裁掉 '.type'
			assertThat(sql)
				.as("%s 必须对 JSON_SEARCH 结果做 -5 裁剪", path)
				.contains("CHAR_LENGTH(JSON_UNQUOTE(JSON_SEARCH(");
			assertThat(sql)
				.as("%s 不得把插入位置直接写成 JSON_SEARCH 原值", path)
				.doesNotContain("JSON_ARRAY_INSERT(\n      `page_content`,\n      JSON_UNQUOTE(JSON_SEARCH(");
		}
	}

	@Test
	void guardUsesStructuralTypeNotEditableTitle() throws IOException {
		for (String path : new String[] { BOOT_PATH, CLOUD_PATH }) {
			String sql = executableLines(read(path));
			// 幂等护栏看 type（结构字段）；按 title 判断会因运营改文案而失效
			assertThat(sql)
				.as("%s 幂等护栏必须按 replenish-card 类型判断", path)
				.contains("'replenish-card', NULL");
			assertThat(sql)
				.as("%s 护栏不得用可编辑标题判断", path)
				.doesNotContain("'今日补给单', NULL");
		}
	}

	@Test
	void insertsCardBeforeKingKong() throws IOException {
		for (String path : new String[] { BOOT_PATH, CLOUD_PATH }) {
			String sql = read(path);
			// 定位金刚区（tab-nav）再插到它前面；硬编码 [0] 会被船舶工作台占位
			assertThat(sql)
				.as("%s 应以 tab-nav 定位插入点", path)
				.contains("'tab-nav', NULL");
			assertThat(sql)
				.as("%s 不得硬编码首位插入", path)
				.doesNotContain("'$[0]'");
		}
	}

	@Test
	void isIdempotentAndReportsFloorCount() throws IOException {
		for (String path : new String[] { BOOT_PATH, CLOUD_PATH }) {
			String sql = read(path);
			// 已存在则跳过（该组件是单例，重复插入会被服务端发布校验拦下）
			assertThat(sql).contains("IS NULL;");
			// 自检要报楼层数与卡片位置，用于发现"误删楼层"和"顺序错位"
			assertThat(sql).contains("draft_floors");
			assertThat(sql).contains("card_after_kingkong");
		}
	}

	@Test
	void fullSqlRegistersTheScript() throws IOException {
		String full = read(FULL_PATH);
		assertThat(full).contains("78home_replenish_card_entry.sql");
		assertThat(full).contains("首页补给单卡片装修迁移");
	}

	private String read(String relativePath) throws IOException {
		return Files.readString(projectRoot.resolve(relativePath));
	}

	/** 去掉 `--` 注释行，只留可执行 SQL —— 断言护栏时不能让注释里的反例自伤 */
	private static String executableLines(String sql) {
		return sql.lines()
			.filter(line -> !line.trim().startsWith("--"))
			.reduce("", (a, b) -> a + "\n" + b);
	}

	private static int countOccurrences(String haystack, String needle) {
		int count = 0;
		int index = haystack.indexOf(needle);
		while (index >= 0) {
			count++;
			index = haystack.indexOf(needle, index + needle.length());
		}
		return count;
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
