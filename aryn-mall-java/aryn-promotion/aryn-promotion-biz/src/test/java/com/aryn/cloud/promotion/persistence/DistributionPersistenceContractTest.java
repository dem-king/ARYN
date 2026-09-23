package com.aryn.cloud.promotion.persistence;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DistributionPersistenceContractTest {

	private final Path javaRoot = findJavaRoot();

	@Test
	void distributionSchemasContainFinancialHardeningColumns() throws IOException {
		for (Path schema : distributionSchemas()) {
			String sql = Files.readString(schema).toLowerCase();
			assertThat(sql).as(schema.toString())
					.contains("pending_commission", "commission_debt", "commission_base_amount",
							"refunded_base_amount", "refunded_commission_amount", "settle_at",
							"payout_no", "payout_time", "payout_by");
		}
	}

	@Test
	void distributionSchemasContainRefundIdempotencyTable() throws IOException {
		for (Path schema : distributionSchemas()) {
			String sql = Files.readString(schema).toLowerCase();
			assertThat(sql).as(schema.toString())
					.contains("create table", "distribution_refund_record", "refund_no", "refund_base_amount")
					.contains("applied", "applied_time", "idx_distribution_refund_pending")
					.contains("unique key", "tenant_id");
		}
	}

	@Test
	void distributionSchemasUseTenantScopedBusinessKeys() throws IOException {
		for (Path schema : distributionSchemas()) {
			String sql = Files.readString(schema).toLowerCase();
			assertThat(sql).as(schema.toString())
					.contains("active_user_id", "unique key", "commission_level")
					.doesNotContain("unique key `uk_distribution_user_user_id` (`user_id`)");
		}
	}

	@Test
	void distributionSchemasEnforceOneActiveConfigPerTenant() throws IOException {
		for (Path schema : distributionSchemas()) {
			String sql = Files.readString(schema).toLowerCase();
			assertThat(sql).as(schema.toString())
				.contains("active_config_key", "status = '0'", "del_flag = '0'",
					"uk_distribution_config_active", "tenant_id");
		}
	}

	@Test
	void userMapperProvidesTenantAwareRowLocks() throws IOException {
		String xml = Files.readString(javaRoot.resolve(
			"aryn-promotion/aryn-promotion-biz/src/main/resources/mapper/DistributionUserMapper.xml"))
			.toLowerCase();
		assertThat(xml)
			.contains("selectbyidforupdate", "selectbyuseridforupdate", "for update");
	}

	@Test
	void withdrawAccountColumnCanStoreVersionedCiphertext() throws IOException {
		for (Path schema : distributionSchemas()) {
			String sql = Files.readString(schema).toLowerCase().replace("`", "");
			assertThat(sql).as(schema.toString()).contains("account_no varchar(512)");
		}
		for (Path migration : List.of(
				javaRoot.resolve("db/boot/13distribution_financial_hardening.sql"),
				javaRoot.resolve("db/cloud/13distribution_financial_hardening.sql"))) {
			String sql = Files.readString(migration).toLowerCase().replace("`", "");
			assertThat(sql).as(migration.toString())
				.contains("modify column account_no varchar(512)");
		}
	}

	@Test
	void cloudPromotionTenantConfigIncludesRefundRecord() throws IOException {
		// 意图：promotion 的租户白名单里必须登记 distribution_refund_record，
		// 否则该表不会被租户拦截器注入 tenant_id（跨租户数据可见）。
		//
		// 原先断言里还带一个 md5（cf60c2c2...）作为内容快照，但它会随
		// 白名单的正常演进（登记新表）而失效 —— 每次加表都要改这个哈希，
		// 而它守卫的其实只是"这段内容存在过"。改为直接解析 promotion 的 content
		// 并断言白名单条目，既守住真实意图，又不会因正常加表而误报。
		String sql = Files.readString(javaRoot.resolve("db/cloud/3aryn_nacos.sql"));
		String content = promotionConfigContent(sql);
		int tablesIndex = content.toLowerCase().indexOf("tables:");
		assertThat(tablesIndex)
				.as("promotion 配置必须包含 hx.tenant.tables 白名单")
				.isGreaterThanOrEqualTo(0);
		String tables = content.substring(tablesIndex);
		assertThat(tables)
				.as("promotion 租户白名单必须登记 distribution_refund_record")
				.contains("- distribution_refund_record");
		assertThat(sql).contains("aryn-promotion-biz-dev.yml");
	}

	/**
	 * 从 3aryn_nacos.sql 的 INSERT 语句里取出 aryn-promotion-biz-dev.yml 的 content。
	 *
	 * <p>该文件把配置存成 MySQL 字符串字面量（换行为 \\n 转义）。正则里的反斜杠
	 * 在 Java 源码里要再转义一层，极易写错（本方法第一版就漏了一层、导致
	 * PatternSyntaxException）；这里改成手工扫描单引号，与
	 * TenantConfigurationConsistencyTest 的解析口径保持一致。
	 */
	private static String promotionConfigContent(String sql) {
		for (String line : sql.split("\n", -1)) {
			if (!line.startsWith("INSERT INTO `config_info` VALUES (")
					|| !line.contains("'aryn-promotion-biz-dev.yml'")) {
				continue;
			}
			List<String> values = parseMysqlStringValues(line);
			// 下标与 TenantConfigurationConsistencyTest 对齐：
			// get(0)=id（数字，也占一列） get(1)=data_id get(2)=group_id get(3)=content
			if (values.size() >= 4) {
				return unescapeMysql(values.get(3));
			}
		}
		throw new IllegalStateException("3aryn_nacos.sql 中未找到 aryn-promotion-biz-dev.yml 配置");
	}

	/** 还原 MySQL 字符串字面量里的转义（\n / \t / \r / \0） */
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

	/** 解析一行 INSERT 里的字段（含无引号的 id / NULL），与官方租户测试同口径 */
	private static List<String> parseMysqlStringValues(String insert) {
		List<String> values = new java.util.ArrayList<>();
		int index = insert.indexOf('(') + 1;
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
						value.append(insert.charAt(index++));
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
			// 无引号字段（如自增 id、NULL）同样占一列，必须一并收集，
			// 否则后续字段整体前移一位（本方法第一版就因此取错了 content）
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

	@Test
	void hardeningMigrationsPreserveLegacyBaseAndNormalizeLevel2OrderIds() throws IOException {
		for (Path migration : List.of(
				javaRoot.resolve("db/boot/13distribution_financial_hardening.sql"),
				javaRoot.resolve("db/cloud/13distribution_financial_hardening.sql"))) {
			String sql = Files.readString(migration).toLowerCase();
			assertThat(sql).as(migration.toString())
					.contains("commission_base_amount decimal(10,2) default null")
					.contains("commission_level = 2", "_l2", "refund_amount")
					.doesNotContain("commission_base_amount = greatest(order_amount, 0)");
		}
	}

	@Test
	void hardeningMigrationsDropLegacyOrderKeyBeforeNormalizingLevel2Ids() throws IOException {
		for (Path migration : List.of(
				javaRoot.resolve("db/boot/13distribution_financial_hardening.sql"),
				javaRoot.resolve("db/cloud/13distribution_financial_hardening.sql"))) {
			String sql = Files.readString(migration).toLowerCase();
			int dropLegacyKey = sql.indexOf("drop index uk_distribution_order_biz_order");
			int normalizeLevel2 = sql.indexOf("set biz_order_id = left");
			int addTenantLevelKey = sql.indexOf("add unique key uk_distribution_order_level");

			assertThat(dropLegacyKey).as(migration + " drop legacy key").isGreaterThanOrEqualTo(0);
			assertThat(normalizeLevel2).as(migration + " normalize level2 ids").isGreaterThan(dropLegacyKey);
			assertThat(addTenantLevelKey).as(migration + " add tenant-level key").isGreaterThan(normalizeLevel2);
		}
	}

	@Test
	void hardeningMigrationsRecountActiveSubordinates() throws IOException {
		for (Path migration : List.of(
				javaRoot.resolve("db/boot/13distribution_financial_hardening.sql"),
				javaRoot.resolve("db/cloud/13distribution_financial_hardening.sql"))) {
			String sql = Files.readString(migration).toLowerCase();
			assertThat(sql).as(migration.toString())
				.contains("subordinate_count", "count(*)", "inviter_user_id", "del_flag = '0'");
		}
	}

	@Test
	void userMapperChecksRefundableDistributionOrdersBeforeDeletion() throws IOException {
		String xml = Files.readString(javaRoot.resolve(
			"aryn-promotion/aryn-promotion-biz/src/main/resources/mapper/DistributionUserMapper.xml"))
			.toLowerCase();
		assertThat(xml)
			.contains("countrefundableorders", "distribution_order", "distributor_user_id")
			.contains("status in ('0', '1')");
	}

	@Test
	void withdrawApplicationPersistsOptionalRemark() throws IOException {
		String dto = Files.readString(javaRoot.resolve(
			"aryn-promotion/aryn-promotion-api/src/main/java/com/aryn/cloud/promotion/api/dto/DistributionWithdrawApplyDTO.java"));
		String service = Files.readString(javaRoot.resolve(
			"aryn-promotion/aryn-promotion-biz/src/main/java/com/aryn/cloud/promotion/service/impl/DistributionWithdrawServiceImpl.java"));
		assertThat(dto).contains("private String remark;");
		assertThat(service).contains("withdraw.setRemark(dto.getRemark())");
	}

	private List<Path> distributionSchemas() {
		return List.of(
				javaRoot.resolve("db/boot/2aryn_boot.sql"),
				javaRoot.resolve("db/cloud/9aryn_promotion.sql"),
				javaRoot.resolve("aryn-promotion/aryn-promotion-biz/src/main/resources/sql/distribution_init.sql"));
	}

	private static Path findJavaRoot() {
		Path current = Path.of("").toAbsolutePath();
		while (current != null && !Files.isDirectory(current.resolve("aryn-promotion"))) {
			current = current.getParent();
		}
		if (current == null) {
			throw new IllegalStateException("无法定位 aryn-mall-java 根目录");
		}
		return current;
	}
}
