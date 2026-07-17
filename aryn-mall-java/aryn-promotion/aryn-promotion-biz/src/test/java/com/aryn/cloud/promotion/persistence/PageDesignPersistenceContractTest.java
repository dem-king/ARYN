package com.aryn.cloud.promotion.persistence;

import com.aryn.cloud.promotion.api.entity.PageDesign;
import com.aryn.cloud.promotion.api.entity.PageDesignTemplate;
import com.aryn.cloud.promotion.api.entity.PageDesignVersion;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PageDesignPersistenceContractTest {

	@Test
	void pageDesignStoresDraftAndPublishedVersionMetadata() {
		assertFields(PageDesign.class, "draftRevision", "schemaVersion", "publishedVersionId", "publishedStatus",
				"publishedAt");
	}

	@Test
	void versionSnapshotsAreTenantOwnedAndLogicallyDeleted() throws NoSuchFieldException {
		assertTable(PageDesignVersion.class, "page_design_version");
		assertFields(PageDesignVersion.class, "pageDesignId", "versionNo", "schemaVersion", "pageContent",
				"publishRemark", "publishBy", "publishedAt", "tenantId", "delFlag");
		Field delFlag = PageDesignVersion.class.getDeclaredField("delFlag");
		assertNotNull(delFlag.getAnnotation(TableLogic.class));
	}

	@Test
	void templatesAreTenantOwnedAndLogicallyDeleted() throws NoSuchFieldException {
		assertTable(PageDesignTemplate.class, "page_design_template");
		assertFields(PageDesignTemplate.class, "templateName", "templateType", "pageType", "templateContent",
				"schemaVersion", "systemFlag", "status", "sort", "tenantId", "delFlag");
		Field delFlag = PageDesignTemplate.class.getDeclaredField("delFlag");
		assertNotNull(delFlag.getAnnotation(TableLogic.class));
	}

	@Test
	void decorationPermissionSeedsStayInBootAndCloudParity() throws IOException {
		String bootSeed = readDatabaseSeed("boot/2aryn_boot.sql");
		String cloudSeed = readDatabaseSeed("cloud/2aryn_upms.sql");
		for (Map.Entry<String, String> menu : Map.of("2026071709000000001", "promotion:pagedesign:publish",
				"2026071709000000002", "promotion:pagedesign:rollback", "2026071709000000003",
				"promotion:pagedesign:template").entrySet()) {
			assertTrue(bootSeed.contains(menu.getValue()), () -> "Boot seed missing " + menu.getValue());
			assertTrue(cloudSeed.contains(menu.getValue()), () -> "Cloud seed missing " + menu.getValue());
			assertTrue(countOccurrences(bootSeed, menu.getKey()) >= 5,
					() -> "Boot seed missing role/tenant grants for " + menu.getValue());
			assertTrue(countOccurrences(cloudSeed, menu.getKey()) >= 6,
					() -> "Cloud seed missing role/tenant grants for " + menu.getValue());
		}
	}

	@Test
	void decorationMigrationsSelectTheirOwningDatabases() throws IOException {
		assertTrue(readDatabaseSeed("boot/10page_design_alter.sql").contains("USE aryn_boot;"));
		assertTrue(readDatabaseSeed("boot/11page_design_publish.sql").contains("USE aryn_boot;"));
		assertTrue(readDatabaseSeed("cloud/10page_design_alter.sql").contains("USE aryn_promotion;"));
		assertTrue(readDatabaseSeed("cloud/11page_design_publish.sql").contains("USE aryn_promotion;"));
	}

	private void assertFields(Class<?> type, String... expectedFields) {
		Set<String> actualFields = Arrays.stream(type.getDeclaredFields())
			.map(Field::getName)
			.collect(Collectors.toSet());
		assertTrue(actualFields.containsAll(Set.of(expectedFields)),
				() -> type.getSimpleName() + " missing fields " + Set.of(expectedFields));
	}

	private void assertTable(Class<?> type, String expectedTable) {
		TableName tableName = type.getAnnotation(TableName.class);
		assertNotNull(tableName);
		assertEquals(expectedTable, tableName.value());
	}

	private String readDatabaseSeed(String relativePath) throws IOException {
		Path current = Path.of("").toAbsolutePath();
		while (current != null && !Files.exists(current.resolve("db"))) {
			current = current.getParent();
		}
		assertNotNull(current, "Cannot locate aryn-mall-java/db");
		return Files.readString(current.resolve("db").resolve(relativePath));
	}

	private int countOccurrences(String source, String value) {
		return (source.length() - source.replace(value, "").length()) / value.length();
	}

}
