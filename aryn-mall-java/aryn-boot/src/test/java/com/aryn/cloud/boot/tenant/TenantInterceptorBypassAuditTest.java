package com.aryn.cloud.boot.tenant;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TenantInterceptorBypassAuditTest {

	private static final String INTERCEPTOR_IGNORE = "@\\s*(?:com\\.baomidou\\.annotation\\.)?InterceptorIgnore";

	private static final Pattern INTERCEPTOR_IGNORE_ANNOTATION = Pattern.compile(INTERCEPTOR_IGNORE + "\\s*\\(");

	private static final Pattern BYPASS_METHOD = Pattern.compile(INTERCEPTOR_IGNORE
			+ "\\s*\\((?=[^)]*tenantLine\\s*=\\s*\"true\")[^)]*\\)\\s+[\\w<>?,.\\s]+\\s+(\\w+)\\s*\\(");

	private static final Set<String> ALLOWED_BYPASSES = Set.of(
			"aryn-message/aryn-message-biz/src/main/java/com/aryn/cloud/message/mapper/MessageConversationMapper.java#selectInactiveConversations",
			"aryn-message/aryn-message-biz/src/main/java/com/aryn/cloud/message/mapper/MessageDispatchTaskMapper.java#selectRecoveryTasks",
			"aryn-message/aryn-message-biz/src/main/java/com/aryn/cloud/message/mapper/MessageChannelTaskMapper.java#resetStaleSending",
			"aryn-message/aryn-message-biz/src/main/java/com/aryn/cloud/message/mapper/MessageChannelTaskMapper.java#selectDueTasks",
			"aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/delivery/mapper/OrderDeliveryEvidenceMapper.java#selectPendingBindings",
			"aryn-pay/aryn-pay-biz/src/main/java/com/aryn/cloud/pay/mapper/PayConfigMapper.java#selectByAppId",
			"aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/mapper/SysMenuMapper.java#selectTenantMenuTree",
			"aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/mapper/SysMaterialMapper.java#releaseExpiredDeliveryReservations",
			"aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/mapper/SysUserMapper.java#selectCount",
			"aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/mapper/SysUserMapper.java#selectUserByName",
			"aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/mapper/SysUserMapper.java#selectUserByPhone",
			"aryn-user/aryn-user-biz/src/main/java/com/aryn/cloud/user/mapper/SocialAccountMapper.java#selectByAppId");

	private final Path projectRoot = findProjectRoot();

	@Test
	void onlyReviewedMapperMethodsMayBypassTenantInterceptor() throws IOException {
		Set<String> actual = new TreeSet<>();
		int annotationCount = 0;
		try (Stream<Path> paths = Files.walk(projectRoot)) {
			for (Path path : paths.filter(TenantInterceptorBypassAuditTest::isMainJavaSource).toList()) {
				String source = Files.readString(path);
				annotationCount += (int) INTERCEPTOR_IGNORE_ANNOTATION.matcher(source).results().count();
				Matcher matcher = BYPASS_METHOD.matcher(source);
				while (matcher.find()) {
					actual.add(projectRoot.relativize(path).toString().replace('\\', '/') + "#" + matcher.group(1));
				}
			}
		}
		assertThat(actual).as("所有 @InterceptorIgnore 都必须被审计解析").hasSize(annotationCount);
		assertThat(actual).containsExactlyInAnyOrderElementsOf(ALLOWED_BYPASSES);
	}

	private static boolean isMainJavaSource(Path path) {
		String normalized = path.toString().replace('\\', '/');
		return normalized.contains("/src/main/java/") && normalized.endsWith(".java");
	}

	@Test
	void tenantMenuBypassUsesExplicitTenantParameter() throws IOException {
		String mapper = Files.readString(projectRoot
				.resolve("aryn-upms/aryn-upms-biz/src/main/resources/mapper/SysMenuMapper.xml"));
		String statement = selectStatement(mapper, "selectTenantMenuTree");

		assertThat(statement).contains("sys_tenant_menu.tenant_id = #{tenantId}");
	}

	@Test
	void messageJobBypassesOnlyDiscoverTenantBoundRecordsBeforeRestoringContext() throws IOException {
		String conversationMapper = Files.readString(projectRoot.resolve(
				"aryn-message/aryn-message-biz/src/main/resources/mapper/MessageConversationMapper.xml"));
		String dispatchMapper = Files.readString(projectRoot.resolve(
				"aryn-message/aryn-message-biz/src/main/resources/mapper/MessageDispatchTaskMapper.xml"));
		String conversationJob = Files.readString(projectRoot.resolve(
				"aryn-message/aryn-message-biz/src/main/java/com/aryn/cloud/message/job/ConversationMaintenanceJob.java"));
		String dispatchService = Files.readString(projectRoot.resolve(
				"aryn-message/aryn-message-biz/src/main/java/com/aryn/cloud/message/service/impl/NoticeDispatchServiceImpl.java"));

		assertThat(selectStatement(conversationMapper, "selectInactiveConversations"))
				.contains("SELECT *", "del_flag = '0'");
		assertThat(selectStatement(dispatchMapper, "selectRecoveryTasks"))
				.contains("SELECT *", "del_flag = '0'");
		assertThat(conversationJob).contains("setTenantId(conversation.getTenantId())", "removeTenantId()");
		assertThat(dispatchService).contains("setTenantId(tenantId)", "removeTenantId()");
	}

	@Test
	void deliveryRecoveryBypassesOnlyTenantDiscoveryAndGlobalExpiredReservationCleanup() throws IOException {
		String evidenceMapper = Files.readString(projectRoot.resolve(
				"aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/delivery/mapper/OrderDeliveryEvidenceMapper.java"));
		String materialMapper = Files.readString(projectRoot.resolve(
				"aryn-upms/aryn-upms-biz/src/main/java/com/aryn/cloud/upms/mapper/SysMaterialMapper.java"));
		String recoveryJob = Files.readString(projectRoot.resolve(
				"aryn-order/aryn-order-biz/src/main/java/com/aryn/cloud/order/delivery/job/DeliveryEvidenceBindingRecoveryJob.java"));

		assertThat(evidenceMapper).contains("binding_status = 'PENDING'", "del_flag = '0'", "LIMIT #{limit}");
		assertThat(materialMapper).contains("binding_status = 'RESERVED'", "reservation_expire_time < NOW()",
				"del_flag = '0'");
		assertThat(recoveryJob).contains("setTenantId(tenantId)", "removeTenantId()", "evidence.getTenantId()");
	}

	@Test
	void userInfoAdminPageUsesExplicitAliasForTenantJoin() throws IOException {
		String mapper = Files.readString(projectRoot
				.resolve("aryn-user/aryn-user-biz/src/main/resources/mapper/UserInfoMapper.xml"));
		String statement = selectStatement(mapper, "selectAdminPage");

		assertThat(statement).contains("LEFT JOIN member_level AS member_level");
	}

	@Test
	void payConfigBypassExcludesLogicallyDeletedCredentials() throws IOException {
		String mapper = Files.readString(
				projectRoot.resolve("aryn-pay/aryn-pay-biz/src/main/resources/mapper/PayConfigMapper.xml"));
		String statement = selectStatement(mapper, "selectByAppId");

		assertThat(statement).contains("pay_config.del_flag = '0'");
	}

	@Test
	void preLoginLookupColumnsAreGloballyUniqueInBootAndCloudSchemas() throws IOException {
		String boot = Files.readString(projectRoot.resolve("db/boot/2aryn_boot.sql"));
		String upms = Files.readString(projectRoot.resolve("db/cloud/2aryn_upms.sql"));
		String user = Files.readString(projectRoot.resolve("db/cloud/4aryn_user.sql"));
		String pay = Files.readString(projectRoot.resolve("db/cloud/6aryn_pay.sql"));

		assertActiveUniqueColumn(boot, "sys_user", "username");
		assertActiveUniqueColumn(boot, "sys_user", "phone");
		assertActiveUniqueColumn(boot, "social_account", "app_id");
		assertActiveUniqueColumn(boot, "pay_config", "app_id");
		assertActiveUniqueColumn(upms, "sys_user", "username");
		assertActiveUniqueColumn(upms, "sys_user", "phone");
		assertActiveUniqueColumn(user, "social_account", "app_id");
		assertActiveUniqueColumn(pay, "pay_config", "app_id");
	}

	@Test
	void uniquenessMigrationsCheckOnlyActiveDuplicatesAndUseFunctionalIndexes() throws IOException {
		for (String migration : Set.of("db/boot/12tenant_lookup_uniqueness.sql",
				"db/cloud/12tenant_lookup_uniqueness.sql")) {
			String sql = Files.readString(projectRoot.resolve(migration));
			assertThat(sql).as(migration).contains("WHERE del_flag = '0'").contains("ADD UNIQUE KEY").contains("((IF(");
		}
	}

	private static void assertActiveUniqueColumn(String sql, String table, String column) {
		String tableDefinition = tableDefinition(sql, table);
		String indexName = "uk_" + table + "_" + column;
		Pattern uniqueIndex = Pattern.compile("(?im)^\\s*UNIQUE\\s+(?:INDEX|KEY)\\s+`?" + Pattern.quote(indexName)
				+ "`?\\s+(\\(.*\\))\\s*,?\\s*$");
		Matcher matcher = uniqueIndex.matcher(tableDefinition);
		assertThat(matcher.find()).as(indexName + " 索引定义").isTrue();
		assertThat(matcher.group(1)).as(indexName + " 有效记录唯一表达式").contains("IF(", "del_flag", column, "id");
	}

	private static String tableDefinition(String sql, String table) {
		Pattern definition = Pattern.compile("(?is)CREATE\\s+TABLE\\s+`?" + Pattern.quote(table)
				+ "`?\\s*\\((.*?)(?:\\)\\s*ENGINE)");
		Matcher matcher = definition.matcher(sql);
		assertThat(matcher.find()).as(table + " 建表语句").isTrue();
		return matcher.group(1);
	}

	private static String selectStatement(String xml, String id) {
		Pattern select = Pattern.compile("(?is)<select\\s+id=\"" + Pattern.quote(id) + "\".*?</select>");
		Matcher matcher = select.matcher(xml);
		assertThat(matcher.find()).as(id + " SQL").isTrue();
		return matcher.group();
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
