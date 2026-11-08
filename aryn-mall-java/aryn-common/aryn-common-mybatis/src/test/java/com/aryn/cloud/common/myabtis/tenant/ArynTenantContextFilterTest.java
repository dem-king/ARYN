package com.aryn.cloud.common.myabtis.tenant;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArynTenantContextFilterTest {

	private final ArynTenantContextFilter filter = new StubUserFilter(null);

	@AfterEach
	void clearTenantContext() {
		ArynTenantContextHolder.removeTenantId();
	}

	/** 用可控会话替换真实 Sa-Token，只验证过滤器的租户绑定分支 */
	private static final class StubUserFilter extends ArynTenantContextFilter {

		private final ArynUser user;

		private StubUserFilter(ArynUser user) {
			this.user = user;
		}

		@Override
		protected ArynUser resolveSessionUser() {
			return user;
		}

	}

	private ArynUser user(String tenantId, boolean platformAdmin, DeviceTypeEnum deviceType) {
		ArynUser user = new ArynUser();
		user.setUserId("user-" + tenantId);
		user.setTenantId(tenantId);
		user.setPlatformAdmin(platformAdmin);
		user.setDeviceType(deviceType);
		return user;
	}

	private MockHttpServletRequest request(String tenantHeader) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		if (tenantHeader != null) {
			request.addHeader(CommonConstants.TENANT_ID, tenantHeader);
		}
		return request;
	}

	private void assertTenantDuringRequest(ArynTenantContextFilter target, MockHttpServletRequest request,
			String expectedTenant) throws Exception {
		target.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) ->
				assertThat(ArynTenantContextHolder.getTenantId()).isEqualTo(expectedTenant));
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	@DisplayName("TOC 会话租户 A + tenant-id 请求头 B：以会话为准，不能跨租户")
	void tocSessionTenantWinsOverHeader() throws Exception {
		assertTenantDuringRequest(new StubUserFilter(user("tenant-a", false, DeviceTypeEnum.TOC)),
				request("tenant-b"), "tenant-a");
	}

	@Test
	@DisplayName("TOB 会话同样以会话租户为准（不区分登录端）")
	void tobSessionTenantWinsOverHeader() throws Exception {
		assertTenantDuringRequest(new StubUserFilter(user("tenant-a", false, DeviceTypeEnum.TOB)),
				request("tenant-b"), "tenant-a");
	}

	@Test
	@DisplayName("平台管理员可合法用 tenant-id 切租户")
	void platformAdminMaySwitchTenant() throws Exception {
		assertTenantDuringRequest(new StubUserFilter(user("platform-tenant", true, DeviceTypeEnum.TOB)),
				request("tenant-b"), "tenant-b");
	}

	@Test
	@DisplayName("平台管理员未带 tenant-id 时留在会话租户")
	void platformAdminWithoutHeaderKeepsSessionTenant() throws Exception {
		assertTenantDuringRequest(new StubUserFilter(user("platform-tenant", true, DeviceTypeEnum.TOB)),
				request(null), "platform-tenant");
	}

	@Test
	@DisplayName("匿名请求继续使用 tenant-id 头（公开接口的租户选择）")
	void anonymousRequestUsesHeaderTenant() throws Exception {
		assertTenantDuringRequest(new StubUserFilter(null), request("tenant-public"), "tenant-public");
	}

	@Test
	void clearsTenantContextAfterSuccessfulRequest() throws Exception {
		MockHttpServletRequest request = request("tenant-a");

		filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) ->
				assertThat(ArynTenantContextHolder.getTenantId()).isEqualTo("tenant-a"));

		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	void clearsTenantContextAfterFailedRequest() {
		MockHttpServletRequest request = request("tenant-b");

		assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(),
				(servletRequest, servletResponse) -> {
					throw new ServletException("downstream failure");
				}))
				.isInstanceOf(ServletException.class)
				.hasMessage("downstream failure");

		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

}
