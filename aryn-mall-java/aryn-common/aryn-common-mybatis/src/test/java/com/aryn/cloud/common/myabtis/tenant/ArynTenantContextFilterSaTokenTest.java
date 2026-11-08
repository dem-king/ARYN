package com.aryn.cloud.common.myabtis.tenant;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.spring.SaTokenContextForSpringInJakartaServlet;
import cn.dev33.satoken.stp.StpUtil;
import com.aryn.cloud.common.core.constant.CacheConstants;
import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实 Sa-Token 下的租户边界回归：凭证放在请求参数（Sa-Token 默认接受）
 * 或请求头，都必须绑定会话所属租户，而不是 tenant-id 请求头。
 */
class ArynTenantContextFilterSaTokenTest {

	private final ArynTenantContextFilter filter = new ArynTenantContextFilter();

	@BeforeAll
	static void configureSaToken() {
		SaTokenConfig config = new SaTokenConfig();
		config.setTokenName(CommonConstants.TOKEN_KEY);
		config.setIsReadCookie(false);
		config.setIsLog(false);
		config.setIsPrint(false);
		SaManager.setConfig(config);
		SaManager.setSaTokenContext(new SaTokenContextForSpringInJakartaServlet());
	}

	@AfterAll
	static void resetSaTokenGlobals() {
		if (SaManager.getSaTokenDao() instanceof SaTokenDaoDefaultImpl dao) {
			dao.destroy();
		}
		// 本类把全局上下文换成了 Servlet 实现；模块内其他测试不依赖请求上下文，复位避免污染
		SaManager.setSaTokenContext(cn.dev33.satoken.context.SaTokenContextDefaultImpl.defaultContext);
		SaManager.setConfig(new SaTokenConfig());
	}

	@AfterEach
	void cleanUp() {
		try {
			StpUtil.logout();
		}
		catch (Exception ignored) {
			// 未登录或上下文已释放时无需处理
		}
		RequestContextHolder.resetRequestAttributes();
		ArynTenantContextHolder.removeTenantId();
	}

	private String login(String userId, String tenantId, DeviceTypeEnum deviceType, boolean platformAdmin) {
		RequestContextHolder.setRequestAttributes(
				new ServletRequestAttributes(new MockHttpServletRequest(), new MockHttpServletResponse()));
		StpUtil.login(userId);
		String token = StpUtil.getTokenValue();
		ArynUser user = new ArynUser();
		user.setUserId(userId);
		user.setTenantId(tenantId);
		user.setDeviceType(deviceType);
		user.setPlatformAdmin(platformAdmin);
		StpUtil.getTokenSession().set(CacheConstants.USER_CACHE, user);
		return token;
	}

	private MockHttpServletRequest requestWithHeaderToken(String token, String tenantHeader) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(CommonConstants.TOKEN_KEY, token);
		if (tenantHeader != null) {
			request.addHeader(CommonConstants.TENANT_ID, tenantHeader);
		}
		return request;
	}

	private MockHttpServletRequest requestWithQueryToken(String token, String tenantHeader) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addParameter(CommonConstants.TOKEN_KEY, token);
		if (tenantHeader != null) {
			request.addHeader(CommonConstants.TENANT_ID, tenantHeader);
		}
		return request;
	}

	private void filterAndAssert(MockHttpServletRequest request, DeviceTypeEnum deviceType, String sessionTenant,
			String effectiveTenant) throws Exception {
		RequestContextHolder.setRequestAttributes(
				new ServletRequestAttributes(request, new MockHttpServletResponse()));
		filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
			StpUtil.checkLogin();
			assertThat(SecurityUtils.requireUser(deviceType).getTenantId()).isEqualTo(sessionTenant);
			assertThat(ArynTenantContextHolder.getTenantId()).isEqualTo(effectiveTenant);
		});
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

	@Test
	@DisplayName("参数令牌 + 冲突 tenant-id：会话租户 A 生效，不能读写 B")
	void queryTokenStillBindsSessionTenant() throws Exception {
		String token = login("toc-user", "tenant-a", DeviceTypeEnum.TOC, false);

		filterAndAssert(requestWithQueryToken(token, "tenant-b"), DeviceTypeEnum.TOC, "tenant-a", "tenant-a");
	}

	@Test
	@DisplayName("请求头令牌 + 冲突 tenant-id：TOC/TOB 会话租户都生效")
	void headerTokenStillBindsSessionTenant() throws Exception {
		String tocToken = login("toc-user", "tenant-a", DeviceTypeEnum.TOC, false);
		filterAndAssert(requestWithHeaderToken(tocToken, "tenant-b"), DeviceTypeEnum.TOC, "tenant-a", "tenant-a");

		StpUtil.logout();
		String tobToken = login("tob-user", "tenant-c", DeviceTypeEnum.TOB, false);
		filterAndAssert(requestWithHeaderToken(tobToken, "tenant-b"), DeviceTypeEnum.TOB, "tenant-c", "tenant-c");
	}

	@Test
	@DisplayName("平台管理员可合法切租户（参数令牌同样适用）")
	void platformAdminMaySwitchTenant() throws Exception {
		String token = login("platform-admin", "platform-tenant", DeviceTypeEnum.TOB, true);

		filterAndAssert(requestWithQueryToken(token, "tenant-b"), DeviceTypeEnum.TOB, "platform-tenant", "tenant-b");
	}

	@Test
	@DisplayName("匿名请求继续按 tenant-id 头选租户")
	void anonymousUsesHeaderTenant() throws Exception {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader(CommonConstants.TENANT_ID, "tenant-public");
		RequestContextHolder.setRequestAttributes(
				new ServletRequestAttributes(request, new MockHttpServletResponse()));
		filter.doFilter(request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {
			assertThat(StpUtil.isLogin()).isFalse();
			assertThat(SecurityUtils.getUser()).isNull();
			assertThat(ArynTenantContextHolder.getTenantId()).isEqualTo("tenant-public");
		});
		assertThat(ArynTenantContextHolder.getTenantId()).isNull();
	}

}
