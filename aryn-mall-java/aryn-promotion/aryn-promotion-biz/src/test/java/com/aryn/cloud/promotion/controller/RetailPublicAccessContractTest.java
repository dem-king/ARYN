package com.aryn.cloud.promotion.controller;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetailPublicAccessContractTest {

	@Test
	void groupBuyActivityReadsArePublicInBootAndCloud() throws IOException {
		Path javaRoot = Path.of("").toAbsolutePath();
		while (javaRoot != null && !Files.exists(javaRoot.resolve("aryn-boot"))) {
			javaRoot = javaRoot.getParent();
		}
		assertNotNull(javaRoot, "Cannot locate aryn-mall-java");
		String bootConfig = Files.readString(javaRoot.resolve("aryn-boot/src/main/resources/application.yml"));
		String cloudConfig = Files.readString(javaRoot.resolve("db/cloud/3aryn_nacos.sql"));

		assertTrue(bootConfig.contains("- /app/groupbuy/activity/**"));
		assertTrue(bootConfig.contains("- /app/groupbuy/record/page"));
		assertTrue(cloudConfig.contains("- /promotion/app/groupbuy/activity/**"));
		assertTrue(cloudConfig.contains("- /promotion/app/groupbuy/record/page"));
	}

}
