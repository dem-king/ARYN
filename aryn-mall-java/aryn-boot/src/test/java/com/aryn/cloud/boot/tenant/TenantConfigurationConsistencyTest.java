package com.aryn.cloud.boot.tenant;

import org.junit.jupiter.api.Test;
import org.assertj.core.api.SoftAssertions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TenantConfigurationConsistencyTest {

	private static final Pattern CREATE_TABLE = Pattern.compile(
			"(?is)CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+`?([a-zA-Z0-9_]+)`?\\s*\\((.*?)(?:\\)\\s*(?:ENGINE|COMMENT|;))");

	private static final Pattern USE_DATABASE = Pattern.compile("(?i)USE\\s+`?([a-zA-Z0-9_]+)`?\\s*;");

	private static final Map<String, String> CLOUD_SCHEMAS = Map.of(
			"aryn-upms-biz-dev.yml", "aryn_upms",
			"aryn-user-biz-dev.yml", "aryn_user",
			"aryn-message-biz-dev.yml", "aryn_message",
			"aryn-pay-biz-dev.yml", "aryn_pay",
			"aryn-order-biz-dev.yml", "aryn_order",
			"aryn-product-biz-dev.yml", "aryn_product",
			"aryn-promotion-biz-dev.yml", "aryn_promotion");

	private static final Set<String> NON_TENANT_DATA_SERVICES = Set.of("aryn-generator-dev.yml");

	private static final Set<String> NON_APPLICATION_DATABASES = Set.of("aryn_nacos");

	private final Path projectRoot = findProjectRoot();

	@Test
	void bootTenantTablesMatchBootSchema() throws IOException {
		Set<String> schemaTables = tenantTablesInDirectory(projectRoot.resolve("db/boot"));
		Set<String> configuredTables = tenantTablesFromYaml(
				Files.readString(projectRoot.resolve("aryn-boot/src/main/resources/application.yml")));

		assertThat(configuredTables).containsExactlyInAnyOrderElementsOf(schemaTables);
	}

	@Test
	void mallDeliveryTablesAreRegisteredInTenantIsolation() throws IOException {
		Set<String> deliveryTables = Set.of(
				"order_delivery_task", "order_delivery_task_item", "order_delivery_evidence",
				"order_delivery_task_log", "order_delivery_area", "sys_user_wechat_binding",
				"message_channel_task");
		Set<String> bootTables = tenantTablesFromYaml(
				Files.readString(projectRoot.resolve("aryn-boot/src/main/resources/application.yml")));
		Map<String, NacosConfig> cloudConfigs = nacosConfigs();
		Set<String> cloudTables = new TreeSet<>();
		for (String dataId : List.of("aryn-order-biz-dev.yml", "aryn-upms-biz-dev.yml", "aryn-message-biz-dev.yml")) {
			cloudTables.addAll(tenantTablesFromYaml(cloudConfigs.get(dataId).content()));
		}

		assertThat(bootTables).containsAll(deliveryTables);
		assertThat(cloudTables).containsAll(deliveryTables);
	}

	@Test
	void cloudTenantTablesMatchEachServiceSchema() throws IOException {
		Map<String, NacosConfig> nacosConfigs = nacosConfigs();
		Map<String, Set<String>> schemaTablesByDatabase = cloudTenantTablesByDatabase();
		Set<String> expectedDatabases = new TreeSet<>(CLOUD_SCHEMAS.values());
		expectedDatabases.addAll(NON_APPLICATION_DATABASES);
		assertThat(schemaTablesByDatabase.entrySet().stream().filter(entry -> !entry.getValue().isEmpty())
				.map(Map.Entry::getKey).collect(java.util.stream.Collectors.toSet()))
				.as("所有含租户表的 Cloud 数据库都必须登记").containsExactlyInAnyOrderElementsOf(expectedDatabases);
		for (Map.Entry<String, String> entry : CLOUD_SCHEMAS.entrySet()) {
			Set<String> schemaTables = schemaTablesByDatabase.getOrDefault(entry.getValue(), Set.of());
			assertThat(tenantTablesFromYaml(nacosConfigs.get(entry.getKey()).content()))
					.as(entry.getKey())
					.containsExactlyInAnyOrderElementsOf(schemaTables);
		}
	}

	private Map<String, Set<String>> cloudTenantTablesByDatabase() throws IOException {
		Map<String, Set<String>> tablesByDatabase = new LinkedHashMap<>();
		try (Stream<Path> paths = Files.list(projectRoot.resolve("db/cloud"))) {
			for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".sql")).toList()) {
				String sql = Files.readString(path);
				Matcher uses = USE_DATABASE.matcher(sql);
				List<DatabaseSection> sections = new ArrayList<>();
				while (uses.find()) {
					sections.add(new DatabaseSection(normalize(uses.group(1)), uses.end(), uses.start()));
				}
				Set<String> unassignedTables = tenantTables(
						sections.isEmpty() ? sql : sql.substring(0, sections.get(0).useStart()));
				assertThat(unassignedTables).as(path.getFileName() + " 租户表必须位于 USE 数据库之后").isEmpty();
				for (int index = 0; index < sections.size(); index++) {
					DatabaseSection section = sections.get(index);
					int end = index + 1 < sections.size() ? sections.get(index + 1).useStart() : sql.length();
					tablesByDatabase.computeIfAbsent(section.database(), ignored -> new TreeSet<>())
							.addAll(tenantTables(sql.substring(section.contentStart(), end)));
				}
			}
		}
		return tablesByDatabase;
	}

	@Test
	void tenantTableListsAreNormalizedAndNacosMd5MatchesContent() throws IOException {
		assertNormalized(tenantTableListFromYaml(
				Files.readString(projectRoot.resolve("aryn-boot/src/main/resources/application.yml"))), "aryn-boot");
		SoftAssertions softly = new SoftAssertions();
		for (Map.Entry<String, NacosConfig> entry : nacosConfigs().entrySet()) {
			if (!CLOUD_SCHEMAS.containsKey(entry.getKey()) && !NON_TENANT_DATA_SERVICES.contains(entry.getKey())) {
				continue;
			}
			if (CLOUD_SCHEMAS.containsKey(entry.getKey())) {
				assertNormalized(tenantTableListFromYaml(entry.getValue().content()), entry.getKey());
			}
			softly.assertThat(entry.getValue().md5()).as(entry.getKey() + " md5")
					.isEqualTo(md5(entry.getValue().content()));
		}
		softly.assertAll();
	}

	@Test
	void nonTenantDataServicesExplicitlyDisableSchemaValidation() throws IOException {
		Map<String, NacosConfig> configs = nacosConfigs();
		for (String dataId : NON_TENANT_DATA_SERVICES) {
			Map<String, Object> tenant = tenantSectionFromYaml(configs.get(dataId).content());
			assertThat(tenant.get("validate-schema")).as(dataId).isEqualTo(false);
			assertThat(tenantTableListFromYaml(configs.get(dataId).content())).as(dataId).isEmpty();
		}
	}

	private Set<String> tenantTablesInDirectory(Path directory) throws IOException {
		Set<String> tables = new TreeSet<>();
		try (Stream<Path> paths = Files.list(directory)) {
			for (Path path : paths.filter(file -> file.getFileName().toString().endsWith(".sql")).toList()) {
				tables.addAll(tenantTables(Files.readString(path)));
			}
		}
		return tables;
	}

	private Set<String> tenantTables(String sql) {
		Set<String> tables = new TreeSet<>();
		Matcher matcher = CREATE_TABLE.matcher(sql);
		while (matcher.find()) {
			if (Pattern.compile("(?i)`?tenant_id`?").matcher(matcher.group(2)).find()) {
				tables.add(normalize(matcher.group(1)));
			}
		}
		return tables;
	}

	private Map<String, NacosConfig> nacosConfigs() throws IOException {
		String sql = Files.readString(projectRoot.resolve("db/cloud/3aryn_nacos.sql"));
		Map<String, NacosConfig> configs = new LinkedHashMap<>();
		for (String line : sql.lines().toList()) {
			if (!line.startsWith("INSERT INTO `config_info` VALUES (")) {
				continue;
			}
			List<String> values = parseInsertValues(line);
			configs.put(values.get(1), new NacosConfig(unescapeMysql(values.get(3)), values.get(4)));
		}
		assertThat(configs).containsKeys(CLOUD_SCHEMAS.keySet().toArray(String[]::new));
		return configs;
	}

	@SuppressWarnings("unchecked")
	private List<String> tenantTableListFromYaml(String yamlContent) {
		Object tables = tenantSectionFromYaml(yamlContent).get("tables");
		return tables instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> tenantSectionFromYaml(String yamlContent) {
		Matcher hxSection = Pattern.compile("(?m)^hx:\\s*$").matcher(yamlContent);
		assertThat(hxSection.find()).as("hx.tenant 配置段").isTrue();
		Map<String, Object> root = new Yaml().load(yamlContent.substring(hxSection.start()));
		Map<String, Object> hx = (Map<String, Object>) root.get("hx");
		return (Map<String, Object>) hx.get("tenant");
	}

	private Set<String> tenantTablesFromYaml(String yamlContent) {
		Set<String> tables = new TreeSet<>();
		for (String table : tenantTableListFromYaml(yamlContent)) {
			tables.add(normalize(table));
		}
		return tables;
	}

	private void assertNormalized(List<String> tables, String source) {
		List<String> normalized = tables.stream().map(TenantConfigurationConsistencyTest::normalize).toList();
		assertThat(tables).as(source + " 空白表名").allMatch(table -> table != null && !table.isBlank());
		assertThat(tables).as(source + " 小写表名").containsExactlyElementsOf(normalized);
		assertThat(new TreeSet<>(normalized)).as(source + " 重复表名").hasSameSizeAs(normalized);
	}

	private static String unescapeMysql(String value) {
		StringBuilder result = new StringBuilder(value.length());
		for (int index = 0; index < value.length(); index++) {
			char current = value.charAt(index);
			if (current != '\\' || index + 1 >= value.length()) {
				result.append(current);
				continue;
			}
			char escaped = value.charAt(++index);
			result.append(switch (escaped) {
				case 'n' -> '\n';
				case 'r' -> '\r';
				case 't' -> '\t';
				case '0' -> '\0';
				default -> escaped;
			});
		}
		return result.toString();
	}

	private static List<String> parseInsertValues(String insert) {
		int index = insert.indexOf('(') + 1;
		List<String> values = new ArrayList<>();
		while (index > 0 && index < insert.length()) {
			while (index < insert.length() && Character.isWhitespace(insert.charAt(index))) {
				index++;
			}
			StringBuilder value = new StringBuilder();
			if (index < insert.length() && insert.charAt(index) == '\'') {
				index++;
				while (index < insert.length()) {
					char current = insert.charAt(index++);
					if (current == '\\' && index < insert.length()) {
						value.append(current).append(insert.charAt(index++));
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
				while (index < insert.length() && insert.charAt(index) != ',' && insert.charAt(index) != ')') {
					value.append(insert.charAt(index++));
				}
			}
			values.add(value.toString().trim());
			while (index < insert.length() && insert.charAt(index) != ',' && insert.charAt(index) != ')') {
				index++;
			}
			if (index >= insert.length() || insert.charAt(index) == ')') {
				break;
			}
			index++;
		}
		return values;
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

	private static String normalize(String value) {
		return value.trim().toLowerCase(Locale.ROOT);
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

	private record NacosConfig(String content, String md5) {
	}

	private record DatabaseSection(String database, int contentStart, int useStart) {
	}

}
