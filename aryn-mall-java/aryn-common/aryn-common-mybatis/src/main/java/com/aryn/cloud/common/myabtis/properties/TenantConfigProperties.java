
package com.aryn.cloud.common.myabtis.properties;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@Configuration
@ConfigurationProperties(prefix = "hx.tenant")
public class TenantConfigProperties {

	// 表名
	private List<String> tables = List.of();

	@Setter(AccessLevel.NONE)
	private Set<String> normalizedTables = Set.of();

	// 启动时校验数据库租户表与白名单是否一致
	private boolean validateSchema = true;

	public void setTables(List<String> tables) {
		this.tables = tables == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(tables));
		this.normalizedTables = this.tables.stream().filter(table -> table != null && !table.isBlank())
				.map(table -> table.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
	}

}
