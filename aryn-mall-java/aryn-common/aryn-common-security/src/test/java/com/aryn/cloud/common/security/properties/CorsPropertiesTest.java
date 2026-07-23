package com.aryn.cloud.common.security.properties;

import com.aryn.cloud.common.core.properties.CorsProperties;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorsPropertiesTest {

	@Test
	void matchesOnlyExplicitConfiguredOrigins() {
		CorsProperties properties = new CorsProperties();
		properties.setAllowedOrigins(List.of(" https://admin.example.com ", "*", ""));

		assertThat(properties.isAllowedOrigin("https://admin.example.com")).isTrue();
		assertThat(properties.isAllowedOrigin("https://evil.example.com")).isFalse();
		assertThat(properties.isAllowedOrigin(null)).isFalse();
		assertThat(properties.getAllowedOrigins()).containsExactly("https://admin.example.com");
	}

	@Test
	void exposesOnlyExplicitApplicationRequestHeaders() {
		CorsProperties properties = new CorsProperties();

		assertThat(properties.getAllowedHeaders()).contains("Content-Type", "satoken", "tenant-id",
				"Accept-Language", "platform-type", "app-id");
		assertThat(properties.getAllowedHeaders()).doesNotContain("*");
		assertThat(properties.getAllowedHeadersValue()).isEqualTo(
				"Content-Type,satoken,tenant-id,Accept-Language,platform-type,app-id");
	}

}
