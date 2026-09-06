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
	void cloudPromotionTenantConfigIncludesRefundRecordAndCommittedDigest() throws IOException {
		String sql = Files.readString(javaRoot.resolve("db/cloud/3aryn_nacos.sql")).toLowerCase();
		assertThat(sql)
				.contains("distribution_refund_record")
				.contains("cf60c2c2528d85820ba9ff9a641f974d")
				.contains("aryn-promotion-biz-dev.yml");
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
