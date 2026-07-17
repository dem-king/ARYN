package com.aryn.cloud.common.myabtis.tenant;

import com.aryn.cloud.common.myabtis.properties.TenantConfigProperties;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantSchemaValidatorTest {

	@Test
	void acceptsConfigurationMatchingTenantTablesIgnoringCase() throws SQLException {
		DataSource dataSource = schema(
				"create table tenant_order (id varchar(32), tenant_id varchar(32))",
				"create table global_menu (id varchar(32))");
		TenantConfigProperties properties = properties(List.of("TENANT_ORDER"));

		assertThatCode(() -> new TenantSchemaValidator(dataSource, properties).validate()).doesNotThrowAnyException();
	}

	@Test
	void rejectsTenantTableMissingFromConfiguration() throws SQLException {
		DataSource dataSource = schema("create table member_level (id varchar(32), tenant_id varchar(32))");

		assertThatThrownBy(() -> new TenantSchemaValidator(dataSource, properties(List.of())).validate())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("未配置租户表: [member_level]");
	}

	@Test
	void rejectsEmptyTenantConfigurationEvenWhenSchemaHasNoTenantTables() throws SQLException {
		DataSource dataSource = schema("create table global_menu (id varchar(32))");

		assertThatThrownBy(() -> new TenantSchemaValidator(dataSource, properties(List.of())).validate())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("租户表白名单为空");
	}

	@Test
	void rejectsConfiguredTableWithoutTenantColumn() throws SQLException {
		DataSource dataSource = schema("create table global_menu (id varchar(32))");

		assertThatThrownBy(() -> new TenantSchemaValidator(dataSource, properties(List.of("global_menu"))).validate())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("误配置租户表: [global_menu]");
	}

	@Test
	void rejectsBlankAndDuplicateConfiguredTables() throws SQLException {
		DataSource dataSource = schema("create table tenant_order (id varchar(32), tenant_id varchar(32))");

		assertThatThrownBy(
				() -> new TenantSchemaValidator(dataSource, properties(List.of("tenant_order", " ", "TENANT_ORDER")))
						.validate())
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("空白表名")
				.hasMessageContaining("重复表名: [tenant_order]");
	}

	private static TenantConfigProperties properties(List<String> tables) {
		TenantConfigProperties properties = new TenantConfigProperties();
		properties.setTables(tables);
		return properties;
	}

	private static DataSource schema(String... statements) throws SQLException {
		JdbcDataSource dataSource = new JdbcDataSource();
		dataSource.setURL("jdbc:h2:mem:tenant-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
		try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
			for (String sql : statements) {
				statement.execute(sql);
			}
		}
		return dataSource;
	}

}
