package com.aryn.cloud.boot.product;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 商超商品主图回填（70 号）的双模式契约。
 *
 * <p>背景：图片是本地存储文件，URL 必须由 {@code /{upms|boot}/file/local/{tenantId}/{uuid}.ext} 组成，
 * 首段随运行模式变化。此前 cloud 侧误写成 {@code /cloud/}，网关没有该路由，回源会落到鉴权并返回
 * 401 JSON——HTTP 200 但内容不是图片，肉眼很难发现。这里把「URL 段、素材库可重复执行、
 * 与仓库图片清单一一对应」固化成回归测试。
 */
class GroceryProductImageSqlContractTest {

	private static final String TENANT = "1590229800633634816";

	private static final String CLOUD_SQL = "db/cloud/70grocery_product_images.sql";

	private static final String BOOT_SQL = "db/boot/70grocery_product_images.sql";

	private static final String MANIFEST = "db/assets/grocery-product-images/manifest.tsv";

	private static final String FULL_SQL = "db/boot/aryn_boot_full.sql";

	private static final int PRODUCT_COUNT = 106;

	private static final Pattern MATERIAL_INSERT = Pattern.compile(
			"INSERT INTO `sys_material`\\s*\\([^)]*\\)\\s*SELECT\\s*'(956\\d{16})', '1', '-1', '([^']*)', '([^']*)',");

	private final Path projectRoot = findProjectRoot();

	@Test
	void cloudUrlsUseGatewayUpmsSegment() throws IOException {
		String cloud = sql(CLOUD_SQL);
		assertThat(urlsIn(cloud)).isNotEmpty().allSatisfy(
				url -> assertThat(url).startsWith("http://localhost:9999/upms/file/local/" + TENANT + "/"));
		assertThat(cloud).as("cloud 模式不应出现网关未路由的 /cloud 首段").doesNotContain("/cloud/");
	}

	@Test
	void bootUrlsUseBootContextPathSegment() throws IOException {
		String boot = sql(BOOT_SQL);
		assertThat(urlsIn(boot)).isNotEmpty().allSatisfy(
				url -> assertThat(url).startsWith("http://localhost:9999/boot/file/local/" + TENANT + "/"));
	}

	@Test
	void bothModesBackfillEveryManifestProductExactlyOnce() throws IOException {
		List<String[]> manifest = manifestRows();
		assertThat(manifest).hasSize(PRODUCT_COUNT);

		List<String> spuIds = manifest.stream().map(row -> row[0]).toList();
		assertThat(new LinkedHashSet<>(spuIds)).as("manifest 商品不应重复").hasSize(PRODUCT_COUNT);

		for (String sqlFile : List.of(CLOUD_SQL, BOOT_SQL)) {
			String sql = sql(sqlFile);
			List<String> updated = new ArrayList<>();
			Matcher matcher = Pattern.compile("WHERE `id` = '(954\\d{16})'").matcher(sql);
			while (matcher.find()) {
				updated.add(matcher.group(1));
			}
			assertThat(updated).as("%s 回填的商品 ID", sqlFile).containsExactlyElementsOf(spuIds);
			assertThat(updated).as("%s 不应重复回填同一 SPU", sqlFile)
				.hasSize(new LinkedHashSet<>(updated).size());
		}
	}

	@Test
	void materialRowsMatchManifestAndSurviveRepeatedRuns() throws IOException {
		List<String[]> manifest = manifestRows();
		for (String sqlFile : List.of(CLOUD_SQL, BOOT_SQL)) {
			String sql = sql(sqlFile);

			List<String> ids = new ArrayList<>();
			Matcher matcher = MATERIAL_INSERT.matcher(sql);
			List<String> urls = new ArrayList<>();
			List<String> names = new ArrayList<>();
			while (matcher.find()) {
				ids.add(matcher.group(1));
				names.add(matcher.group(2));
				urls.add(matcher.group(3));
			}
			assertThat(ids).as("%s 素材条数", sqlFile).hasSize(PRODUCT_COUNT);
			assertThat(new LinkedHashSet<>(ids)).as("%s 素材 ID 不应重复", sqlFile).hasSize(PRODUCT_COUNT);

			// sys_material.id 没有唯一键，ON DUPLICATE KEY UPDATE 不会生效；
			// 必须 UPDATE + INSERT..WHERE NOT EXISTS，否则重复执行会累积重复素材行。
			assertThat(sql).as("%s 不可依赖 ON DUPLICATE KEY UPDATE", sqlFile)
				.doesNotContain("ON DUPLICATE KEY UPDATE");
			assertThat(countOf(sql, "FROM DUAL WHERE NOT EXISTS"))
				.as("%s 缺少 NOT EXISTS 兜底", sqlFile).isEqualTo(PRODUCT_COUNT);

			// 素材名取源图文件名（如「油麦菜 约300g-份.jpg」），与 manifest 的 asset_path 末段一致
			Set<String> expected = manifest.stream()
				.map(row -> row[2].substring(row[2].lastIndexOf('/') + 1))
				.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
			assertThat(new LinkedHashSet<>(names)).as("%s 素材名应与 manifest 对应", sqlFile)
				.containsExactlyInAnyOrderElementsOf(expected);
		}
	}

	@Test
	void manifestReferencesEveryStoredFileNameAndMatchesSeedProducts() throws IOException {
		List<String[]> manifest = manifestRows();
		// 清单里的商品名必须与 41grocery_catalog_seed.sql 的 954x 种子商品名逐字对应，
		// 否则图片会被挂到错误的商品上（本文件的匹配正是按名称做的）。
		String seed = sql("db/boot/41grocery_catalog_seed.sql");
		Set<String> seedNames = new LinkedHashSet<>();
		Matcher seedMatcher = Pattern.compile("\\('954\\d{16}', '([^']*)'").matcher(seed);
		while (seedMatcher.find()) {
			seedNames.add(seedMatcher.group(1));
		}
		assertThat(seedNames).as("41 号种子商品数").hasSize(PRODUCT_COUNT);
		assertThat(manifest.stream().map(row -> row[1]).toList())
			.as("清单商品名应与种子完全一致").containsExactlyInAnyOrderElementsOf(seedNames);

		for (String[] row : manifest) {
			assertThat(row[0]).startsWith("954").hasSize(19);
			assertThat(row[4]).as("文件大小应为正整数").matches("\\d+");
			Path asset = projectRoot.resolve("db/assets/grocery-product-images").resolve(row[2]);
			assertThat(Files.isRegularFile(asset)).as("仓库图片缺失: %s", row[2]).isTrue();
			assertThat(Files.size(asset)).as("清单体积与文件不一致: %s", row[2])
				.isEqualTo(Long.parseLong(row[4]));
			assertThat(row[3]).as("存储名应为 uuid.jpg").matches("[0-9a-f-]{36}\\.jpg");
		}
	}

	@Test
	void fullSqlMergesTheSameBackfill() throws IOException {
		String full = sql(FULL_SQL);
		assertThat(full).contains("Source: db/boot/70grocery_product_images.sql");
		assertThat(urlsIn(full)).allSatisfy(
				url -> assertThat(url).startsWith("http://localhost:9999/boot/file/local/" + TENANT + "/"));
	}

	private List<String[]> manifestRows() throws IOException {
		List<String[]> rows = new ArrayList<>();
		for (String line : Files.readAllLines(
				projectRoot.resolve(MANIFEST), StandardCharsets.UTF_8)) {
			if (line.isBlank() || line.startsWith("spu_id\t")) {
				continue;
			}
			String[] cells = line.split("\t", -1);
			assertThat(cells).as("manifest 列数: %s", line).hasSize(5);
			rows.add(cells);
		}
		return rows;
	}

	private static List<String> urlsIn(String sql) {
		List<String> urls = new ArrayList<>();
		Matcher matcher = Pattern.compile(
				"'(http://localhost:9999/[a-z]+/file/local/\\d{19}/[0-9a-f-]{36}\\.jpg)'").matcher(sql);
		while (matcher.find()) {
			urls.add(matcher.group(1));
		}
		return urls;
	}

	private static int countOf(String haystack, String needle) {
		int count = 0;
		int index = haystack.indexOf(needle);
		while (index >= 0) {
			count++;
			index = haystack.indexOf(needle, index + needle.length());
		}
		return count;
	}

	private String sql(String relative) throws IOException {
		return Files.readString(projectRoot.resolve(relative), StandardCharsets.UTF_8).replace("\r\n", "\n");
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
