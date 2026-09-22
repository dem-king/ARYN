package com.aryn.cloud.boot.promotion;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * boot 模式增量脚本的库名守门。
 *
 * <p>背景（2026-09-22 端到端实测发现）：boot 目录下 74 / 75 号脚本整段是从
 * cloud 版复制而来，`USE` 写的是 cloud 库名（`aryn_promotion` / `aryn_product` /
 * `aryn_upms`）。boot 全量初始化只建 `aryn_boot` 与 `aryn_boot_job` 两个库，
 * 因此执行到 74 号就报 `Unknown database` ——
 * <b>而 mysql 客户端在默认（非 --force）模式下遇错即整段中止</b>，
 * 导致其后的 75 / 76 / 77 / 78 号脚本与 XXL-JOB 全都没执行。
 *
 * <p>这类缺陷的隐蔽性在于：
 * <ul>
 *   <li>单跑某个脚本是正常的（手工建过该库时就看不出来）；</li>
 *   <li>静态校验 verify-full-sql.mjs 只查结构，不查库名；</li>
 *   <li>只有「空库跑一遍全量」才会暴露，而这恰恰是部署路径。</li>
 * </ul>
 *
 * <p>所以这里做静态守门：boot 目录下所有脚本的 `USE` 只能指向 boot 自己的库。
 * cloud 目录的脚本**不受此约束**（各微服务本来就有独立库），因此不检查。
 */
class BootSqlSchemaTargetTest {

	/** boot 全量初始化会创建的库（见 db/boot/1schema.sql 与 3aryn_boot_job.sql） */
	private static final Set<String> BOOT_SCHEMAS = Set.of("aryn_boot", "aryn_boot_job");

	private static final Pattern USE_STATEMENT = Pattern.compile("^\\s*USE\\s+`?([A-Za-z0-9_]+)`?\\s*;",
			Pattern.MULTILINE);

	private final Path projectRoot = findProjectRoot();

	@Test
	@DisplayName("boot 目录下所有脚本只 USE boot 自己的库")
	void bootScriptsOnlyUseBootSchemas() throws IOException {
		Path bootDir = projectRoot.resolve("db/boot");
		List<String> violations = new ArrayList<>();

		try (Stream<Path> files = Files.list(bootDir)) {
			for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".sql")).toList()) {
				String name = file.getFileName().toString();
				// 全量产物本身是拼接结果，单独在上面用全量文件校验更直观；
				// 这里只看增量脚本，避免同一问题被报两次。
				if ("aryn_boot_full.sql".equals(name)) {
					continue;
				}
				Matcher matcher = USE_STATEMENT.matcher(Files.readString(file));
				while (matcher.find()) {
					String schema = matcher.group(1);
					if (!BOOT_SCHEMAS.contains(schema)) {
						violations.add(name + " -> USE `" + schema + "`");
					}
				}
			}
		}

		assertThat(violations)
			.as("""
				boot 增量脚本只能使用 %s。出现其它库名说明该脚本是从 cloud 版复制的，
				boot 全量初始化没有这个库，执行到该行会报 Unknown database 并**中止后续所有脚本**
				（mysql 默认遇错即停）。""".formatted(BOOT_SCHEMAS))
			.isEmpty();
	}

	@Test
	@DisplayName("cloud 目录脚本不得误用 boot 库名（反向约束）")
	void cloudScriptsDoNotUseBootSchema() throws IOException {
		Path cloudDir = projectRoot.resolve("db/cloud");
		List<String> violations = new ArrayList<>();

		try (Stream<Path> files = Files.list(cloudDir)) {
			for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".sql")).toList()) {
				Matcher matcher = USE_STATEMENT.matcher(Files.readString(file));
				while (matcher.find()) {
					// cloud 各微服务库名形如 aryn_order；命中纯 boot 库名才是错误。
					// aryn_boot_job 是 XXL-JOB 自有库，cloud 侧也可能引用，放行。
					if ("aryn_boot".equals(matcher.group(1))) {
						violations.add(file.getFileName() + " -> USE `aryn_boot`");
					}
				}
			}
		}

		assertThat(violations)
			.as("cloud 目录脚本不得引用 boot 单体库")
			.isEmpty();
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
