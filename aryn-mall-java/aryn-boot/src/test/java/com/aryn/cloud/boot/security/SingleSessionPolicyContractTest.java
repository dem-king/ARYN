package com.aryn.cloud.boot.security;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 全局单点登录策略契约测试。
 *
 * <p>背景：Sa-Token 默认 {@code is-concurrent=true}，同一账号可在多台电脑/多个浏览器
 * 同时在线；业务要求「同一账号任意时刻只保留一个有效会话，新登录顶掉旧登录」
 * （管理端 Web 与配送端登录同为 TOB 设备类型会互顶，C 端 TOC 同样单设备）。
 *
 * <p>该策略由配置驱动，没有业务代码可兜底：boot 改一处 yml、cloud 改 Nacos 共享配置，
 * 任何一侧漏改都会出现「同一个账号在个别环境仍能多端登录」的隐性偏差。因此这里把
 * 三方绑成契约：
 *
 * <ol>
 *   <li>boot 单体 {@code aryn-boot/application.yml} 的 {@code sa-token.is-concurrent} 为 false；</li>
 *   <li>cloud 基线 {@code db/cloud/3aryn_nacos.sql} 的共享配置 {@code application-dev.yml} 同值，
 *       且 md5 与 content 一致（Nacos 靠 md5 判断配置是否变化）；</li>
 *   <li>存量 cloud 环境靠增量脚本 {@code 166global_single_session_nacos_config.sql} 升级，
 *       脚本必须幂等、重算 md5，并带锚点失效自检。</li>
 * </ol>
 */
class SingleSessionPolicyContractTest {

	private static final String BOOT_APPLICATION = "aryn-boot/src/main/resources/application.yml";

	private static final String CLOUD_NACOS_SEED = "db/cloud/3aryn_nacos.sql";

	private static final String CLOUD_INCREMENT_SCRIPT = "db/cloud/166global_single_session_nacos_config.sql";

	private final Path projectRoot = findProjectRoot();

	@Test
	void bootApplicationEnforcesSingleSessionPerAccount() throws IOException {
		Map<String, Object> saToken = saTokenSection(Files.readString(projectRoot.resolve(BOOT_APPLICATION)));
		assertThat(saToken.get("is-concurrent"))
				.as("boot 必须开启全局单点登录：sa-token.is-concurrent 应为 false")
				.isEqualTo(false);
		// is-share=false：每次登录签发新 token（与单点互顶配合，旧 token 会被 REPLACED 标记）
		assertThat(saToken.get("is-share"))
				.as("sa-token.is-share 应保持 false，否则顶号会复用旧 token")
				.isEqualTo(false);
	}

	@Test
	void bootProfileFilesDoNotSilentlyOverrideSessionPolicy() throws IOException {
		for (String profile : List.of("application-dev.yml", "application-prod.yml")) {
			Path file = projectRoot.resolve("aryn-boot/src/main/resources/" + profile);
			assertThat(file).as(profile + " 应存在").exists();
			assertThat(Files.readString(file))
					.as(profile + " 不得覆盖 sa-token.is-concurrent，单点策略以 application.yml 为准")
					.doesNotContain("is-concurrent");
		}
	}

	@Test
	void cloudSharedConfigEnforcesSingleSessionPerAccount() throws IOException {
		NacosConfig config = sharedApplicationConfig();
		assertThat(config.content())
				.as("cloud 共享配置 application-dev.yml 应已改为单点登录")
				.contains("is-concurrent: false");
		assertThat(saTokenValue(config.content(), "is-concurrent"))
				.as("cloud 共享配置 sa-token.is-concurrent 应为 false")
				.isEqualTo(false);
		assertThat(config.md5())
				.as("Nacos config_info.md5 必须与 content 一致，否则配置变更不会被服务感知")
				.isEqualTo(md5(config.content()));
	}

	@Test
	void cloudIncrementScriptUpgradesExistingEnvironmentsIdempotently() throws IOException {
		Path script = projectRoot.resolve(CLOUD_INCREMENT_SCRIPT);
		assertThat(script).as("存量 cloud 环境的升级脚本必须存在").exists();
		String sql = Files.readString(script);

		assertThat(sql).contains("'application-dev.yml'");
		assertThat(sql).contains("'is-concurrent: true'");
		assertThat(sql).contains("'is-concurrent: false'");
		// 幂等：必须重算 md5，且不得重建配置
		assertThat(sql).contains("`md5` = MD5(@app_content)");
		assertThat(sql).contains("`gmt_modified` = NOW()");
		// 锚点失效时必须由结尾自检暴露，不能「执行成功但内容没改」
		assertThat(sql).contains("single_session_registered");
		assertThat(sql).contains("legacy_multi_session_left");
		assertThat(sql).contains("config_md5_mismatch");
		assertThat(sql.toUpperCase())
				.as("Nacos 配置增量脚本不得删除/重建配置")
				.doesNotContain("DROP ", "TRUNCATE");
	}

	@Test
	void nacosSeedConfigRowsKeepFullColumnCount() throws IOException {
		// 2026-09 曾有人在手工编辑本 dump 时丢了 config_info 后 7 列（gmt_create~tenant_id），
		// 行内值数从 17 变成 10，导致 cloud 空库初始化执行到该行直接报 1136 中断。
		String sql = Files.readString(projectRoot.resolve(CLOUD_NACOS_SEED));
		List<String> malformed = new ArrayList<>();
		int rows = 0;
		int lineNo = 0;
		for (String line : sql.split("\n", -1)) {
			lineNo++;
			if (!line.startsWith("INSERT INTO `config_info` VALUES (")) {
				continue;
			}
			rows++;
			int columns = parseMysqlStringValues(line).size();
			if (columns != 17) {
				malformed.add("line " + lineNo + " -> " + columns + " columns");
			}
		}
		assertThat(rows).as("3aryn_nacos.sql 应至少包含一行 config_info 种子配置").isPositive();
		assertThat(malformed)
				.as("config_info 共 17 列（见同文件 CREATE TABLE）；缺列会让 cloud 空库导入在该行中断")
				.isEmpty();
	}

	private NacosConfig sharedApplicationConfig() throws IOException {
		String sql = Files.readString(projectRoot.resolve(CLOUD_NACOS_SEED));
		for (String line : sql.split("\n", -1)) {
			if (!line.startsWith("INSERT INTO `config_info` VALUES (")) {
				continue;
			}
			List<String> values = parseMysqlStringValues(line);
			// INSERT 的 id 是未加引号的数字，也被解析为第 0 个值：
			// get(0)=id get(1)=data_id get(2)=group_id get(3)=content get(4)=md5
			if (!"application-dev.yml".equals(values.get(1))) {
				continue;
			}
			return new NacosConfig(unescapeMysql(values.get(3)), values.get(4));
		}
		throw new IllegalStateException("3aryn_nacos.sql 中未找到 application-dev.yml 共享配置");
	}

	/**
	 * 解析 yaml 里的 sa-token 段为 Map。
	 *
	 * <p>不能直接 {@code new Yaml().load(整个文件)}：boot 的 application.yml 含
	 * {@code @project.artifactId@} 这类以保留字符 {@code @} 开头的标量，SnakeYAML 会解析失败。
	 * 因此只截取 sa-token 段（同 TenantConfigurationConsistencyTest 只截 hx 段的思路）。
	 */
	private static Map<String, Object> saTokenSection(String yaml) {
		List<String> block = extractBlock(yaml, "sa-token:");
		assertThat(block).as("未找到 sa-token 配置段").isNotEmpty();
		Object loaded = new Yaml().load(String.join("\n", block));
		assertThat(loaded).isInstanceOf(Map.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> root = (Map<String, Object>) loaded;
		@SuppressWarnings("unchecked")
		Map<String, Object> saToken = (Map<String, Object>) root.get("sa-token");
		assertThat(saToken).as("sa-token 配置段解析失败").isNotNull();
		return saToken;
	}

	private static Boolean saTokenValue(String content, String key) {
		List<String> block = extractBlock(content, "sa-token:");
		assertThat(block).as("未找到 sa-token 配置段").isNotEmpty();
		Object loaded = new Yaml().load(String.join("\n", block));
		@SuppressWarnings("unchecked")
		Map<String, Object> root = (Map<String, Object>) loaded;
		@SuppressWarnings("unchecked")
		Map<String, Object> saToken = (Map<String, Object>) root.get("sa-token");
		return (Boolean) saToken.get(key);
	}

	/** 从首行 `sa-token:` 起截取到下一个顶格 key 之前（保留缩进子项） */
	private static List<String> extractBlock(String yaml, String header) {
		String[] lines = yaml.split("\n", -1);
		List<String> block = new ArrayList<>();
		boolean started = false;
		for (String line : lines) {
			if (!started) {
				if (line.trim().equals(header)) {
					started = true;
					block.add(line);
				}
				continue;
			}
			if (!line.isBlank() && !Character.isWhitespace(line.charAt(0))) {
				break;
			}
			block.add(line);
		}
		return block;
	}

	private static List<String> parseMysqlStringValues(String line) {
		List<String> values = new ArrayList<>();
		int index = line.indexOf('(') + 1;
		while (index < line.length()) {
			while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
				index++;
			}
			if (index >= line.length()) {
				break;
			}
			StringBuilder value = new StringBuilder();
			if (line.charAt(index) == '\'') {
				index++;
				while (index < line.length()) {
					char current = line.charAt(index++);
					if (current == '\\' && index < line.length()) {
						value.append(current).append(line.charAt(index++));
					}
					else if (current == '\'') {
						break;
					}
					else {
						value.append(current);
					}
				}
			}
			else {
				while (index < line.length() && line.charAt(index) != ',' && line.charAt(index) != ')') {
					value.append(line.charAt(index++));
				}
			}
			values.add(value.toString().trim());
			while (index < line.length() && line.charAt(index) != ',' && line.charAt(index) != ')') {
				index++;
			}
			if (index >= line.length() || line.charAt(index) == ')') {
				break;
			}
			index++;
		}
		return values;
	}

	/** 还原 MySQL 字符串字面量里的转义（\n / \t / \r / \' / \\ 等） */
	private static String unescapeMysql(String value) {
		StringBuilder result = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char current = value.charAt(i);
			if (current != '\\' || i + 1 >= value.length()) {
				result.append(current);
				continue;
			}
			char next = value.charAt(++i);
			result.append(switch (next) {
				case 'n' -> '\n';
				case 't' -> '\t';
				case 'r' -> '\r';
				case '0' -> '\0';
				case 'b' -> '\b';
				case 'Z' -> '\u001a';
				default -> next;
			});
		}
		return result.toString();
	}

	private static String md5(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
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

	private record NacosConfig(String content, String md5) {
	}

}
