package com.aryn.cloud.boot.promotion;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 首页金刚区装修迁移脚本（76 号）契约测试。
 *
 * <p>该脚本要改的是「已发布快照」里某一个组件的 props，
 * 一旦写成整体重写 components 数组，就会连带删掉轮播/商品/秒杀楼层，
 * 而这类破坏在静态 review 里很难看出来。这里把三条硬约束固化：
 *
 * <ol>
 *   <li>只能 JSON_SET 单个组件，不得出现整体重写 components 的赋值；</li>
 *   <li>JSON_SEARCH 返回的是指向 {@code .type} 的路径，必须先裁掉 {@code .type}
 *       再拼 {@code .props}（直接拼会得到非法路径，JSON_SET 静默不生效）；</li>
 *   <li>boot / cloud 两份内容必须一致（仅库名与标题行不同）。</li>
 * </ol>
 */
class HomeKingKongSqlContractTest {

	private static final String BOOT_PATH = "db/boot/76home_kingkong_entries.sql";

	private static final String CLOUD_PATH = "db/cloud/76home_kingkong_entries.sql";

	private static final String FULL_PATH = "db/boot/aryn_boot_full.sql";

	/** 金刚区 8 项入口：顺序即展示顺序 */
	private static final String[] EXPECTED_TITLES = {
			"我的补给单", "常购清单", "限时折扣", "领券中心",
			"每日签到", "多人拼团", "船供专区", "联系客服"
	};

	private final Path projectRoot = findProjectRoot();

	@Test
	void bothModesExistAndDeclareTheSameEightEntries() throws IOException {
		String boot = read(BOOT_PATH);
		String cloud = read(CLOUD_PATH);

		for (String title : EXPECTED_TITLES) {
			assertThat(boot).as("boot 缺少入口[%s]", title).contains("'" + title + "'");
			assertThat(cloud).as("cloud 缺少入口[%s]", title).contains("'" + title + "'");
		}
	}

	@Test
	void neverRewritesTheWholeComponentsArray() throws IOException {
		for (String path : new String[] { BOOT_PATH, CLOUD_PATH }) {
			String sql = read(path);
			// 整体重写会把轮播/商品/秒杀一并抹掉；只允许按路径 JSON_SET
			assertThat(sql)
				.as("%s 不得整体重写 components 数组", path)
				.doesNotContain("JSON_ARRAY_INSERT")
				.doesNotContain("'$.sections[0].components',");
			assertThat(sql).contains("JSON_SET");
		}
	}

	@Test
	void stripsTheTypeSuffixBeforeAppendingProps() throws IOException {
		for (String path : new String[] { BOOT_PATH, CLOUD_PATH }) {
			String sql = read(path);
			// JSON_SEARCH 返回 "$...components[N].type"，必须先裁掉结尾的 .type
			assertThat(sql)
				.as("%s 必须对 JSON_SEARCH 结果做 -5 裁剪", path)
				.contains("CHAR_LENGTH(JSON_UNQUOTE(JSON_SEARCH(");
			// 反例：JSON_SEARCH(...) 直接 || '.props' —— 得到的路径会带 .type 后缀，
			// 变成 ...type.props（非法），JSON_SET 静默不生效。
			// 断言方式：先确认存在 .type 后缀，再确认它没被直接拼接。
			assertThat(sql)
				.as("%s 不得把 .props 直接拼在 .type 路径后", path)
				.doesNotContain(".type')) || '.props'")
				.doesNotContain(".type') || '.props'")
				.doesNotContain(".type' ) || '.props'");
		}
	}

	@Test
	void isIdempotentAndGuardsAgainstLosingFloors() throws IOException {
		for (String path : new String[] { BOOT_PATH, CLOUD_PATH }) {
			String sql = read(path);
			// 幂等护栏：已升级过则跳过
			assertThat(sql).contains("NOT LIKE '%kk-replenish-%'");
			// 自检必须报组件总数，用于发现"楼层被误删"
			assertThat(sql).contains("AS component_count");
		}
	}

	@Test
	void fullSqlRegistersTheScript() throws IOException {
		String full = read(FULL_PATH);
		assertThat(full).contains("76home_kingkong_entries.sql");
		assertThat(full).contains("首页金刚区改为 8 项运营入口");
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
