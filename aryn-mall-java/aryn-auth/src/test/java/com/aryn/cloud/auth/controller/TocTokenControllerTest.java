package com.aryn.cloud.auth.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.auth.service.LoginTenantGuard;
import com.aryn.cloud.auth.service.TocLoginService;
import com.aryn.cloud.user.api.dto.UserLoginReqDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TocTokenController 契约测试：header 强制覆盖、binding 端点最小公开契约、
 * tenant-session 的 401/403 语义分离（方案 §8.3/§8.5/§12.3）。
 */
@ExtendWith(MockitoExtension.class)
class TocTokenControllerTest {

	@Mock
	private TocLoginService tocLoginService;

	@Mock
	private LoginTenantGuard loginTenantGuard;

	private MockedStatic<SecurityUtils> securityUtils;

	private MockedStatic<StpUtil> stpUtil;

	@BeforeEach
	void setUp() {
		// CALLS_REAL_METHODS：requireDevice 的设备比对走真实实现（设备错配必须真抛 403），
		// getUser 逐用例显式 stub
		securityUtils = mockStatic(SecurityUtils.class, CALLS_REAL_METHODS);
		stpUtil = mockStatic(StpUtil.class);
	}

	@AfterEach
	void cleanUp() {
		securityUtils.close();
		stpUtil.close();
	}

	private TocTokenController controller() {
		return new TocTokenController(tocLoginService, loginTenantGuard);
	}

	private MockHttpServletRequest wxMaRequest() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("tenant-id", "tenant-1");
		request.addHeader("app-id", "wx0a8242ea59f3e6b4");
		request.addHeader("platform-type", "WX_MA");
		return request;
	}

	@Test
	void applyRequestIdentityAlwaysOverwritesBodyWithRawHeaders() {
		UserLoginReqDTO dto = new UserLoginReqDTO();
		// 客户端 body 伪造的身份字段，必须被原始请求头无条件覆盖
		dto.setRequestTenantId("spoofed-tenant");
		dto.setAppId("spoofed-app-id");
		dto.setPlatformType("H5");

		controller().applyRequestIdentity(wxMaRequest(), dto);

		assertThat(dto.getRequestTenantId()).isEqualTo("tenant-1");
		assertThat(dto.getAppId()).isEqualTo("wx0a8242ea59f3e6b4");
		assertThat(dto.getPlatformType()).isEqualTo("WX_MA");
	}

	@Test
	void tenantBindingReturnsMinimalPublicPayloadOnly() {
		when(loginTenantGuard.requireVerifiedTenant("wx0a8242ea59f3e6b4", "tenant-1")).thenReturn("tenant-1");

		Map<String, Object> payload = controller().tenantBinding(wxMaRequest()).getData();

		// 公开返回仅 appId/tenantId/ready：不暴露 accountId/配置指纹等内部字段
		assertThat(payload).containsOnlyKeys("appId", "tenantId", "ready");
		assertThat(payload.get("ready")).isEqualTo(Boolean.TRUE);
	}

	@Test
	void tenantBindingRejectsMissingHeadersWithoutCallingGuard() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("app-id", "wx0a8242ea59f3e6b4");

		assertThatThrownBy(() -> controller().tenantBinding(request))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getMsg())
			.isEqualTo("缺少 app-id 或 tenant-id 请求头");
		verifyNoInteractions(loginTenantGuard);
	}

	@Test
	void tenantSessionRequiresLoginEvenWhenPathIsWhitelisted() {
		// /toc-token/** 在免登白名单内：无登录必须由 Controller 显式转 401
		securityUtils.when(SecurityUtils::getUser).thenReturn(null);

		assertThatThrownBy(() -> controller().tenantSession(wxMaRequest(), "mall"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getCode())
			.isEqualTo(401);
	}

	@Test
	void tenantSessionRejectsDeviceMismatchAs403() {
		ArynUser user = new ArynUser();
		user.setUserId("user-1");
		user.setTenantId("tenant-1");
		user.setDeviceType(DeviceTypeEnum.TOC);
		securityUtils.when(SecurityUtils::getUser).thenReturn(user);
		stpUtil.when(StpUtil::isLogin).thenReturn(true);
		// requireDevice 保持真实实现：TOC 会话请求 delivery scope 必须设备错配 403

		assertThatThrownBy(() -> controller().tenantSession(wxMaRequest(), "delivery"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getCode())
			.isEqualTo(403);
	}

	@Test
	void tenantSessionRejectsTenantMismatchAs403() {
		ArynUser user = new ArynUser();
		user.setUserId("user-1");
		user.setTenantId("tenant-other");
		user.setDeviceType(DeviceTypeEnum.TOC);
		securityUtils.when(SecurityUtils::getUser).thenReturn(user);
		stpUtil.when(StpUtil::isLogin).thenReturn(true);

		assertThatThrownBy(() -> controller().tenantSession(wxMaRequest(), "mall"))
			.isInstanceOf(ArynBusinessException.class)
			.extracting(e -> ((ArynBusinessException) e).getCode())
			.isEqualTo(403);
	}

	@Test
	void tenantSessionReturnsSessionTenantOnSuccess() {
		ArynUser user = new ArynUser();
		user.setUserId("user-1");
		user.setTenantId("tenant-1");
		user.setDeviceType(DeviceTypeEnum.TOC);
		securityUtils.when(SecurityUtils::getUser).thenReturn(user);
		stpUtil.when(StpUtil::isLogin).thenReturn(true);

		Map<String, Object> payload = controller().tenantSession(wxMaRequest(), "mall").getData();

		assertThat(payload).containsOnlyKeys("tenantId");
		assertThat(payload.get("tenantId")).isEqualTo("tenant-1");
	}

}
