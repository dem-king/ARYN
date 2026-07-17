package com.aryn.cloud.common.myabtis.tenant;

import com.aryn.cloud.common.myabtis.properties.TenantConfigProperties;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

public class TenantSchemaValidator {

	private static final String TENANT_COLUMN = "tenant_id";

	private final DataSource dataSource;

	private final TenantConfigProperties properties;

	public TenantSchemaValidator(DataSource dataSource, TenantConfigProperties properties) {
		this.dataSource = dataSource;
		this.properties = properties;
	}

	public void validate() {
		List<String> errors = new ArrayList<>();
		Set<String> configuredTables = configuredTables(errors);
		Set<String> schemaTables = tenantTablesFromSchema();
		if (configuredTables.isEmpty()) {
			errors.add("租户表白名单为空");
		}

		Set<String> missingTables = new TreeSet<>(schemaTables);
		missingTables.removeAll(configuredTables);
		if (!missingTables.isEmpty()) {
			errors.add("未配置租户表: " + missingTables);
		}

		Set<String> unexpectedTables = new TreeSet<>(configuredTables);
		unexpectedTables.removeAll(schemaTables);
		if (!unexpectedTables.isEmpty()) {
			errors.add("误配置租户表: " + unexpectedTables);
		}

		if (!errors.isEmpty()) {
			throw new IllegalStateException("多租户 schema 校验失败: " + String.join("; ", errors));
		}
	}

	private Set<String> configuredTables(List<String> errors) {
		Set<String> normalizedTables = new TreeSet<>();
		Set<String> duplicateTables = new TreeSet<>();
		boolean hasBlank = false;
		List<String> tables = properties.getTables();
		if (tables == null) {
			tables = List.of();
		}
		for (String table : tables) {
			if (table == null || table.isBlank()) {
				hasBlank = true;
				continue;
			}
			String normalizedTable = normalize(table);
			if (!normalizedTables.add(normalizedTable)) {
				duplicateTables.add(normalizedTable);
			}
		}
		if (hasBlank) {
			errors.add("存在空白表名");
		}
		if (!duplicateTables.isEmpty()) {
			errors.add("重复表名: " + duplicateTables);
		}
		return normalizedTables;
	}

	private Set<String> tenantTablesFromSchema() {
		try (Connection connection = dataSource.getConnection()) {
			DatabaseMetaData metadata = connection.getMetaData();
			String catalog = connection.getCatalog();
			Set<String> ordinaryTables = ordinaryTables(metadata, catalog);
			Set<String> tenantTables = new TreeSet<>();
			try (ResultSet columns = metadata.getColumns(catalog, null, "%", "%")) {
				while (columns.next()) {
					String tableName = normalize(columns.getString("TABLE_NAME"));
					String columnName = normalize(columns.getString("COLUMN_NAME"));
					if (ordinaryTables.contains(tableName) && TENANT_COLUMN.equals(columnName)) {
						tenantTables.add(tableName);
					}
				}
			}
			return tenantTables;
		}
		catch (SQLException ex) {
			throw new IllegalStateException("读取数据库 schema 失败", ex);
		}
	}

	private Set<String> ordinaryTables(DatabaseMetaData metadata, String catalog) throws SQLException {
		Set<String> tables = new HashSet<>();
		try (ResultSet resultSet = metadata.getTables(catalog, null, "%", new String[] { "TABLE" })) {
			while (resultSet.next()) {
				tables.add(normalize(resultSet.getString("TABLE_NAME")));
			}
		}
		return tables;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
	}

}
