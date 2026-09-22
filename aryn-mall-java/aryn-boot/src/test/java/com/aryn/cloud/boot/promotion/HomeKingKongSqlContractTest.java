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
			"每日签到", "多人拼团", "限时秒杀", "联系客服"
	};

	/**
	 * 「船供专区」不得出现在金刚区。
	 *
	 * <p>2026-09-20 决策 D1：船供不是商品分类，是采购场景，C 端不区分展示
	 * （商品统一走商品分类浏览）。2026-09-22 已裁决金刚区不再设该入口，
	 * 原型第 2 格由「限时秒杀」补位。
	 *
	 * <p>注意区分：`ship-supply` **页面**仍要保留，它是共享购物车的选货模式入口
	 * （`?scene=2&sharedCartId=...`）。这里禁的是金刚区里的专区入口，不是页面本身。
	 */
	private static final String FORBIDDEN_TITLE = "船供专区";

	private final Path projectRoot = findProjectRoot();

	/** 草稿段 + 已发布快照段，每段各一条 navList 条目定义 */
	private static final int SECTIONS_PER_SCRIPT = 2;

	/**
	 * 金刚区一条 navList 条目的定义。断言必须落在**条目定义**上：
	 * <ul>
	 *   <li>只数标题会把 `name`（与标题同名时）算进来，次数翻倍；</li>
	 *   <li>只数落地页 URL 会把幂等护栏里那个判据也算进来。</li>
	 * </ul>
	 */
	private static String navEntry(String title, String linkName, String url) {
		return "JSON_OBJECT('title', '" + title + "', 'url', '',\n"
			+ "                  'link', JSON_OBJECT('name', '" + linkName + "', 'url', '" + url + "'))";
	}

	@Test
	void bothModesExistAndDeclareTheSameEightEntries() throws IOException {
		// 必须按**出现次数**校验，不能只判 contains：
		// 只改坏草稿段（或只改坏已发布段）时，文件整体仍然 contains 该串，
		// 断言会静默放过——实测注入「只把草稿里的拼团入口改坏」时正是如此。
		for (String path : new String[] { BOOT_PATH, CLOUD_PATH }) {
			String sql = read(path);
			for (String title : EXPECTED_TITLES) {
				String titleDef = "'title', '" + title + "', 'url', ''";
				assertThat(countOccurrences(sql, titleDef))
					.as("%s 入口[%s] 应恰好出现 %d 次（草稿+已发布）", path, title, SECTIONS_PER_SCRIPT)
					.isEqualTo(SECTIONS_PER_SCRIPT);
			}
		}
	}

	@Test
	void doesNotReintroduceShipSupplyZoneEntry() throws IOException {
		for (String path : new String[] { BOOT_PATH, CLOUD_PATH }) {
			String sql = read(path);
			// 断言必须只看 navList 的条目定义，不能全文匹配 —— 头部注释正解释
			// 「为什么不含船供专区」，全文匹配会自我误伤。
			assertThat(sql)
				.as("%s 不得把「船供专区」写进金刚区 navList", path)
				.doesNotContain("'title', '" + FORBIDDEN_TITLE + "'");
			// 也不得指向专区落地页
			assertThat(sql)
				.as("%s 金刚区不得链接 ship-supply 专区页", path)
				.doesNotContain("'link', JSON_OBJECT('name', '船供采购'");
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
			// 幂等护栏必须是**真实存在的内容判据**。
			//
			// 原先写的是 `NOT LIKE '%kk-replenish-%'`，但脚本从未把该串写进 JSON，
			// 属死代码：重跑会重新覆盖整个 props，把运营在装修后台的改动静默抹掉
			// （已用真实数据在临时表实测确认）。这里守住它不被改回去。
			// 只看可执行语句：注释里正解释「为什么不能退回 kk- 护栏」，
			// 全文匹配会自我误伤（本断言的第一版就栽在这里）。
			assertThat(executableLines(sql))
				.as("%s 不得退回永不命中的 kk- 标记护栏", path)
				.doesNotContain("kk-replenish");
			assertThat(sql)
				.as("%s 护栏必须用结构性的 link.url 判据", path)
				.contains("'/pages/promotion/seckill', NULL");
			// 自检必须报组件总数，用于发现"楼层被误删"
			assertThat(sql).contains("AS component_count");
		}
	}

	@Test
	void guardUsesStructuralUrlNotEditableTitle() throws IOException {
		// 护栏用 title 判断是不安全的：运营把「我的补给单」改成「我的补给清单」
		// 之后护栏就失效，重跑会覆盖整组（实测已复现）。必须用 link.url。
		for (String path : new String[] { BOOT_PATH, CLOUD_PATH }) {
			String sql = read(path);
			assertThat(sql)
				.as("%s 护栏不得以运营可改的 title 作判据", path)
				.doesNotContain("'one', '限时秒杀', NULL");
			assertThat(sql)
				.as("%s 护栏应对草案与已发布快照各生效一次", path)
				.contains("link.url') IS NULL");
		}
	}

	/** 金刚区各入口的落地页：入口名 -> 小程序路由（顺序与 EXPECTED_TITLES 对应） */
	private static final String[][] EXPECTED_LINKS = {
			{ "我的补给单", "共享购物车列表", "/sub-pages/order/shared-cart/list" },
			{ "常购清单", "常购清单", "/sub-pages/product/frequent/index" },
			{ "限时折扣", "限时折扣", "/pages/promotion/discount" },
			{ "领券中心", "优惠券列表", "/sub-pages/promotion/coupon/coupon-list/index" },
			{ "每日签到", "每日签到", "/sub-pages/user/member/sign-in" },
			{ "多人拼团", "拼团列表", "/sub-pages/promotion/group-buy/group-buy-list/index" },
			{ "限时秒杀", "限时秒杀", "/pages/promotion/seckill" },
			{ "联系客服", "客服会话", "/sub-pages/message/chat/index" },
	};

	@Test
	void everyEntryPointsAtADeclaredPage() throws IOException {
		// 入口指向不存在的路由 → 点击后静默无响应。装修数据是运营配置，
		// 这类漂移既不会被类型检查发现，也不会被编译发现。
		//
		// pages.json 的路由拼接规则：
		//   · 主包：path 字段自带 `pages/` 前缀（`pages/promotion/discount`）；
		//   · 子包：{ root } + pages[].path，root 不写进 path
		//            （root=`sub-pages` + path=`order/shared-cart/list`）。
		// 所以这里只剥子包的 root，主包原样匹配。
		String pages = Files.readString(findRepoRoot().resolve("aryn-mall-uniapp/src/pages.json"));

		for (String[] entry : EXPECTED_LINKS) {
			String title = entry[0];
			String linkName = entry[1];
			String url = entry[2];
			String relative = url.startsWith("/sub-pages/")
				? url.substring("/sub-pages/".length())
				: url.substring(1);
			assertThat(pages)
				.as("入口[%s]指向的 %s 未在 pages.json 声明", title, url)
				.contains("\"path\": \"" + relative + "\"");

			for (String path : new String[] { BOOT_PATH, CLOUD_PATH }) {
				// 按**完整条目定义**计数：只改坏其中一段时 contains 会静默放过，
				// 而只数 URL 又会把幂等护栏里的同一个 URL 算进来。
				assertThat(countOccurrences(read(path), navEntry(title, linkName, url)))
					.as("%s 入口[%s]的条目定义应恰好出现 %d 次", path, title, SECTIONS_PER_SCRIPT)
					.isEqualTo(SECTIONS_PER_SCRIPT);
			}
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

	private static int countOccurrences(String haystack, String needle) {
		int count = 0;
		int index = haystack.indexOf(needle);
		while (index >= 0) {
			count++;
			index = haystack.indexOf(needle, index + needle.length());
		}
		return count;
	}

	/** 去掉 `--` 注释行，只留可执行 SQL —— 断言护栏时不能让注释里的反例自伤 */
	private static String executableLines(String sql) {
		return sql.lines()
			.filter(line -> !line.trim().startsWith("--"))
			.reduce("", (a, b) -> a + "\n" + b);
	}

	/** 仓库根（含 aryn-mall-java / aryn-mall-uniapp 同级目录），用于跨端路由核对 */
	private static Path findRepoRoot() {
		Path javaRoot = findProjectRoot();
		Path repoRoot = javaRoot.getParent();
		if (repoRoot == null || !Files.isDirectory(repoRoot.resolve("aryn-mall-uniapp"))) {
			throw new IllegalStateException("未找到包含 aryn-mall-uniapp 的仓库根目录");
		}
		return repoRoot;
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
