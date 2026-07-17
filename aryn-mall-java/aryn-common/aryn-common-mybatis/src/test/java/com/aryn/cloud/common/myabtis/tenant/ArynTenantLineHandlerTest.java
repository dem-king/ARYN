package com.aryn.cloud.common.myabtis.tenant;

import com.aryn.cloud.common.myabtis.properties.TenantConfigProperties;
import net.sf.jsqlparser.expression.NullValue;
import net.sf.jsqlparser.expression.StringValue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArynTenantLineHandlerTest {

	@Test
	void tenantTableConfigurationRequiresRestartToChange() {
		assertThat(TenantConfigProperties.class.getAnnotation(RefreshScope.class)).isNull();
	}

	@Test
	void normalizedTenantTablesCannotBeBoundIndependently() {
		boolean hasDerivedSetter = Arrays.stream(TenantConfigProperties.class.getMethods())
				.anyMatch(method -> method.getName().equals("setNormalizedTables"));

		assertThat(hasDerivedSetter).isFalse();
	}

	@Test
	void configuredTenantTablesCannotBeMutatedWithoutRebuildingDerivedSet() {
		TenantConfigProperties properties = new TenantConfigProperties();
		properties.setTables(List.of("tenant_order"));

		assertThatThrownBy(() -> properties.getTables().add("tenant_user"))
				.isInstanceOf(UnsupportedOperationException.class);
	}

	@AfterEach
	void clearTenantContext() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void matchesConfiguredTenantTableIgnoringCase() {
		ArynTenantLineHandler handler = handler(List.of("tenant_order"));

		assertThat(handler.ignoreTable("TENANT_ORDER")).isFalse();
	}

	@Test
	void ignoresTableWhenTenantConfigurationIsEmpty() {
		ArynTenantLineHandler handler = handler(List.of());

		assertThat(handler.ignoreTable("global_menu")).isTrue();
	}

	@Test
	void returnsNullExpressionWithoutTenantContext() {
		assertThat(handler(List.of()).getTenantId()).isInstanceOf(NullValue.class);
	}

	@Test
	void returnsCurrentTenantAsStringExpression() {
		ArynTenantContextHolder.setTenantId("tenant-a");

		StringValue tenantId = (StringValue) handler(List.of()).getTenantId();
		assertThat(tenantId.getValue()).isEqualTo("tenant-a");
	}

	private static ArynTenantLineHandler handler(List<String> tables) {
		TenantConfigProperties properties = new TenantConfigProperties();
		properties.setTables(tables);
		ArynTenantLineHandler handler = new ArynTenantLineHandler();
		ReflectionTestUtils.setField(handler, "tenantConfigProperties", properties);
		return handler;
	}

}
