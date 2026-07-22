package com.aryn.cloud.message.schema;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class MessageSchemaContractTest {

	private static final List<String> TABLES = List.of("message_notice", "message_recipient",
			"message_dispatch_task", "message_conversation", "message_participant", "message_chat",
			"message_agent", "message_assignment_log");

	private static final List<String> REQUIRED_COLUMNS = List.of("id", "tenant_id", "create_by", "update_by",
			"create_time", "update_time", "del_flag");

	private static final Pattern CREATE_TABLE = Pattern.compile(
			"(?is)CREATE\\s+TABLE(?:\\s+IF\\s+NOT\\s+EXISTS)?\\s+`?([a-zA-Z0-9_]+)`?\\s*\\((.*?)(?:\\)\\s*(?:ENGINE|COMMENT|;))");

	private final Path projectRoot = findProjectRoot();

	@Test
	void bootAndCloudSchemasContainRequiredMessageTablesAndAuditColumns() throws IOException {
		assertSchema(projectRoot.resolve("db/cloud/20message_center.sql"), "aryn_message");
		assertSchema(projectRoot.resolve("db/boot/20message_center.sql"), "aryn_boot");
	}

	@Test
	void messageSchemasContainConcurrencyAndIdempotencyIndexes() throws IOException {
		for (Path schema : schemaFiles()) {
			String sql = normalize(Files.readString(schema));
			assertThat(sql).contains("`card_payload` json");
			assertThat(sql).contains("unique key `uk_message_recipient_identity` (`tenant_id`, `message_id`, `recipient_type`, `recipient_id`)");
			assertThat(sql).contains("unique key `uk_message_chat_seq` (`tenant_id`, `conversation_id`, `seq_no`)");
			assertThat(sql).contains("unique key `uk_message_chat_client` (`tenant_id`, `sender_type`, `sender_id`, `client_message_id`)");
			assertThat(sql).contains("unique key `uk_message_conversation_customer_active` (`tenant_id`, `customer_active_key`)");
			assertThat(sql).contains("unique key `uk_message_conversation_staff_active` (`tenant_id`, `staff_active_key`)");
		}
	}

	private void assertSchema(Path schema, String database) throws IOException {
		assertThat(schema).exists();
		String sql = Files.readString(schema);
		assertThat(normalize(sql)).contains("use `" + database + "`");
		Matcher matcher = CREATE_TABLE.matcher(sql);
		int matched = 0;
		while (matcher.find()) {
			String table = matcher.group(1).toLowerCase(Locale.ROOT);
			if (!TABLES.contains(table)) {
				continue;
			}
			matched++;
			String definition = normalize(matcher.group(2));
			for (String column : REQUIRED_COLUMNS) {
				assertThat(definition).as(table + "." + column).contains("`" + column + "`");
			}
			assertThat(definition).as(table + " primary key").contains("primary key (`id`)");
		}
		assertThat(matched).isEqualTo(TABLES.size());
	}

	private List<Path> schemaFiles() {
		return List.of(projectRoot.resolve("db/cloud/20message_center.sql"),
				projectRoot.resolve("db/boot/20message_center.sql"));
	}

	private static String normalize(String value) {
		return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
	}

	private static Path findProjectRoot() {
		Path current = Path.of("").toAbsolutePath();
		while (current != null && !Files.isDirectory(current.resolve("db/cloud"))) {
			current = current.getParent();
		}
		if (current == null) {
			throw new IllegalStateException("无法定位 aryn-mall-java 项目根目录");
		}
		return current;
	}

}
