package com.aryn.cloud.common.myabtis.tenant;

import com.aryn.cloud.common.core.constant.CommonConstants;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArynTenantContextFilterTest {

	private final ArynTenantContextFilter filter = new ArynTenantContextFilter();

	@AfterEach
	void clearTenantContext() {
		ArynTenantContextHolder.removeTenantId();
	}

	@Test
	void clearsTenantContextAfterSuccessfulRequest() throws Exception {
		MockHttpServletRequest request = requestWithTenant("tenant-a");

		filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) ->
				assertThat(ArynTenantContextHolder.getTenantId()).isEqualTo("tenant-a"));

		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void clearsTenantContextAfterFailedRequest() {
		MockHttpServletRequest request = requestWithTenant("tenant-b");

		assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(),
				(servletRequest, servletResponse) -> {
					throw new ServletException("downstream failure");
				}))
				.isInstanceOf(ServletException.class)
				.hasMessage("downstream failure");

		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	private MockHttpServletRequest requestWithTenant(String tenantId) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(CommonConstants.TENANT_ID, tenantId);
		return request;
	}

}
