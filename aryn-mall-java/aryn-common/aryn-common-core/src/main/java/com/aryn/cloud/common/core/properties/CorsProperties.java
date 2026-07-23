package com.aryn.cloud.common.core.properties;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Getter
@Component
@ConfigurationProperties(prefix = "hx.cors")
public class CorsProperties {

	private List<String> allowedOrigins = List.of("http://localhost:5777");

	private List<String> allowedHeaders = List.of("Content-Type", "satoken", "tenant-id", "Accept-Language",
			"platform-type", "app-id");

	public void setAllowedOrigins(List<String> allowedOrigins) {
		if (allowedOrigins == null) {
			this.allowedOrigins = List.of();
			return;
		}
		this.allowedOrigins = allowedOrigins.stream()
			.filter(StringUtils::hasText)
			.map(String::trim)
			.filter(origin -> !"*".equals(origin))
			.distinct()
			.toList();
	}

	public boolean isAllowedOrigin(String origin) {
		return StringUtils.hasText(origin) && allowedOrigins.contains(origin.trim());
	}

	public void setAllowedHeaders(List<String> allowedHeaders) {
		if (allowedHeaders == null) {
			this.allowedHeaders = List.of();
			return;
		}
		this.allowedHeaders = allowedHeaders.stream()
			.filter(StringUtils::hasText)
			.map(String::trim)
			.filter(header -> !"*".equals(header))
			.distinct()
			.toList();
	}

	public String getAllowedHeadersValue() {
		return String.join(",", allowedHeaders);
	}

}
