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
 * 登录安全加固配置契约（2026-10-28）。
 *
 * <p>背景：这些策略完全由配置驱动，没有业务代码兜底，boot 与 cloud 任一侧漏改就会出现
 * 「开发环境已加固、生产仍打印完整 token / 仍用 30 天管理端会话」的隐性偏差。因此把三方绑成契约：</p>
 * <ol>
 *   <li>boot {@code aryn-boot/application.yml}：{@code sa-token.is-log=false}、
 *       {@code security.login.*} 生效、密码相关 Mapper 日志不得为 DEBUG；</li>
 *   <li>cloud 基线 {@code db/cloud/3aryn_nacos.sql}：共享配置与 auth/upms/user 配置同值，
 *       且每行 {@code md5} 与 {@code content} 一致（Nacos 靠 md5 判断是否变更）；</li>
 *   <li>存量 cloud 环境靠增量脚本 {@code db/cloud/170login_hardening_nacos_config.sql} 升级，
 *       脚本必须幂等、重算 md5，并带锚点失效自检。</li>
 * </ol>
 */
class LoginSecurityConfigContractTest {

	private static final String BOOT_APPLICATION = "aryn-boot/src/main/resources/application.yml";

	private static final String CLOUD_NACOS_SEED = "db/cloud/3aryn_nacos.sql";

	private static final String CLOUD_INCREMENT_SCRIPT = "db/cloud/170login_hardening_nacos_config.sql";

	private static final String SHARED_APP = "application-dev.yml";

	private static final String AUTH_APP = "aryn-auth-dev.yml";

	private static final String UPMS_APP = "aryn-upms-biz-dev.yml";

	private static final String USER_APP = "aryn-user-biz-dev.yml";

	private final Path projectRoot = findProjectRoot();

	@Test
	void bootApplicationDisablesTokenLoggingAndEnablesLoginProtection() throws IOException {
		String yaml = Files.readString(projectRoot.resolve(BOOT_APPLICATION));

		Map<String, Object> saToken = topLevelSection(yaml, "sa-token:");
		assertThat(saToken.get("is-log")).as("必须关闭 Sa-Token 完整 token 日志").isEqualTo(false);
		// 单点登录策略仍在（166 脚本与 SingleSessionPolicyContractTest 管）
		assertThat(saToken.get("is-concurrent")).isEqualTo(false);

		Map<String, Object> security = topLevelSection(yaml, "security:");
		Map<String, Object> login = nestedMap(security, "login");
		assertThat(login.get("enabled")).as("登录失败限流必须启用").isEqualTo(true);
		assertThat(login.get("max-account-failures")).isEqualTo(5);
		assertThat(login.get("account-lock-seconds")).isEqualTo(900);
		assertThat(login.get("max-ip-failures")).isEqualTo(30);
		assertThat(login.get("ip-lock-seconds")).isEqualTo(900);
		assertThat(login.get("tob-timeout-seconds"))
			.as("管理端会话绝对有效期必须从 30 天缩短（默认 12 小时）")
			.isEqualTo(43200);
	}

	@Test
	void bootMapperLoggingNeverLogsPasswordHashes() throws IOException {
		String yaml = Files.readString(projectRoot.resolve(BOOT_APPLICATION));
		Map<String, Object> logging = topLevelSection(yaml, "logging:");
		Object level = logging.get("level");
		assertThat(level).isInstanceOf(Map.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> levels = (Map<String, Object>) level;
		// 新增/改密 SQL 参数含 BCrypt hash，DEBUG 会写进日志
		for (String mapper : List.of("com.aryn.cloud.upms.mapper", "com.aryn.cloud.user.mapper")) {
			assertThat(levels.get(mapper))
				.as(mapper + " 不得为 DEBUG")
				.isNotEqualTo("DEBUG");
		}
	}

	@Test
	void cloudSharedConfigDisablesTokenLogging() throws IOException {
		NacosConfig config = nacosConfig(SHARED_APP);
		assertThat(config.content()).contains("is-log: false");
		assertThat(config.content()).doesNotContain("is-log: true");
		assertThat(config.md5()).as("Nacos md5 必须与 content 一致").isEqualTo(md5(config.content()));
	}

	@Test
	void cloudAuthConfigRegistersLoginProtection() throws IOException {
		NacosConfig config = nacosConfig(AUTH_APP);
		assertThat(config.content()).contains("tob-timeout-seconds: 43200");
		assertThat(config.content()).contains("max-account-failures: 5");
		assertThat(config.md5()).isEqualTo(md5(config.content()));
	}

	@Test
	void cloudPasswordMappersDoNotStayOnDebug() throws IOException {
		NacosConfig upms = nacosConfig(UPMS_APP);
		assertThat(upms.content()).contains("com.aryn.cloud.upms.mapper: info");
		assertThat(upms.content()).doesNotContain("com.aryn.cloud.upms.mapper: debug");
		assertThat(upms.md5()).isEqualTo(md5(upms.content()));

		NacosConfig user = nacosConfig(USER_APP);
		assertThat(user.content()).contains("com.aryn.cloud.user.mapper: info");
		assertThat(user.content()).doesNotContain("com.aryn.cloud.user.mapper: debug");
		assertThat(user.md5()).isEqualTo(md5(user.content()));
	}

	@Test
	void cloudIncrementScriptUpgradesExistingEnvironmentsIdempotently() throws IOException {
		Path script = projectRoot.resolve(CLOUD_INCREMENT_SCRIPT);
		assertThat(script).as("存量 cloud 环境的升级脚本必须存在").exists();
		String sql = Files.readString(script);

		// 三方都要覆盖
		assertThat(sql).contains("'application-dev.yml'");
		assertThat(sql).contains("'aryn-auth-dev.yml'");
		assertThat(sql).contains("'aryn-upms-biz-dev.yml'");
		assertThat(sql).contains("'aryn-user-biz-dev.yml'");
		// 锚点与目标值
		assertThat(sql).contains("'is-log: true'");
		assertThat(sql).contains("'is-log: false'");
		assertThat(sql).contains("com.aryn.cloud.upms.mapper: debug");
		assertThat(sql).contains("com.aryn.cloud.upms.mapper: info");
		assertThat(sql).contains("com.aryn.cloud.user.mapper: debug");
		assertThat(sql).contains("com.aryn.cloud.user.mapper: info");
		assertThat(sql).contains("tob-timeout-seconds");
		// 幂等：必须重算 md5，且不得重建配置
		assertThat(sql).contains("`md5` = MD5(");
		assertThat(sql).contains("`gmt_modified` = NOW()");
		// 锚点失效时必须由结尾自检暴露
		assertThat(sql).contains("token_log_disabled");
		assertThat(sql).contains("legacy_token_log_left");
		assertThat(sql).contains("upms_mapper_info");
		assertThat(sql).contains("user_mapper_info");
		assertThat(sql).contains("login_guard_registered");
		assertThat(sql).contains("config_md5_mismatch");
		assertThat(sql.toUpperCase()).as("Nacos 配置增量脚本不得删除/重建配置").doesNotContain("DROP ", "TRUNCATE");
	}

	private NacosConfig nacosConfig(String dataId) throws IOException {
		String sql = Files.readString(projectRoot.resolve(CLOUD_NACOS_SEED));
		for (String line : sql.split("\n", -1)) {
			if (!line.startsWith("INSERT INTO `config_info` VALUES (")) {
				continue;
			}
			List<String> values = parseMysqlStringValues(line);
			// get(0)=id get(1)=data_id get(2)=group_id get(3)=content get(4)=md5
			if (!dataId.equals(values.get(1))) {
				continue;
			}
			return new NacosConfig(unescapeMysql(values.get(3)), values.get(4));
		}
		throw new IllegalStateException(CLOUD_NACOS_SEED + " 中未找到配置：" + dataId);
	}

	/**
	 * 截取 YAML 顶格 key 起的完整配置段（保留缩进子项）。
	 *
	 * <p>boot 的 application.yml 含 {@code @project.artifactId@}，整文件 SnakeYAML 解析会失败，
	 * 因此只取目标段。用块级解析能同时拿到嵌套 map 与布尔值。</p>
	 */
	private static Map<String, Object> topLevelSection(String yaml, String header) {
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
		assertThat(block).as("未找到配置段：" + header).isNotEmpty();
		Object loaded = new Yaml().load(String.join("\n", block));
		assertThat(loaded).as(header + " 解析失败").isInstanceOf(Map.class);
		@SuppressWarnings("unchecked")
		Map<String, Object> root = (Map<String, Object>) loaded;
		@SuppressWarnings("unchecked")
		Map<String, Object> section = (Map<String, Object>) root.get(header.substring(0, header.length() - 1));
		assertThat(section).as(header + " 段为空").isNotNull();
		return section;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> nestedMap(Map<String, Object> root, String key) {
		Object value = root.get(key);
		assertThat(value).as("缺少配置段：" + key).isInstanceOf(Map.class);
		return (Map<String, Object>) value;
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
