package com.aryn.cloud.auth.service;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.stp.SaTokenInfo;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.crypto.digest.BCrypt;
import com.aryn.cloud.auth.config.LoginProtectionProperties;
import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.upms.api.entity.SysUser;
import com.aryn.cloud.upms.api.remote.RemoteSysUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 管理端登录安全契约：
 * <ol>
 *   <li>账号不存在 / 密码错误返回同一提示（防账号枚举）；</li>
 *   <li>失败按账号/IP 计数，锁定后不再查库验密；</li>
 *   <li>登录成功前清空密码 hash，且管理端会话使用缩短后的绝对有效期；</li>
 *   <li>配送登录同样受限流保护。</li>
 * </ol>
 */
class LoginServiceSecurityTest {

	private static final String STRONG_PASSWORD = "Aryn2026Pass";

	private RemoteSysUserService remoteSysUserService;

	private TenantAccessGuard tenantAccessGuard;

	private LoginAttemptGuard loginAttemptGuard;

	private LoginProtectionProperties properties;

	private LoginService loginService;

	private MockedStatic<SecurityUtils> securityUtils;

	private MockedStatic<StpUtil> stpUtil;

	private SaTokenConfig previousSaTokenConfig;

	@BeforeEach
	void setUp() {
		// 模拟 boot/cloud 的全局单点登录配置（application.yml / Nacos 中 is-concurrent=false），
		// 用于断言登录入口不会把该策略覆盖回 true
		previousSaTokenConfig = SaManager.getConfig();
		SaManager.setConfig(new SaTokenConfig().setIsConcurrent(false)
			.setIsShare(false)
			.setIsPrint(false));
		remoteSysUserService = mock(RemoteSysUserService.class);
		tenantAccessGuard = mock(TenantAccessGuard.class);
		loginAttemptGuard = mock(LoginAttemptGuard.class);
		properties = new LoginProtectionProperties();
		loginService = new LoginService(remoteSysUserService, mock(ApplicationEventPublisher.class),
				tenantAccessGuard, loginAttemptGuard, properties);
		securityUtils = mockStatic(SecurityUtils.class);
		stpUtil = mockStatic(StpUtil.class);
		stpUtil.when(StpUtil::getTokenInfo).thenReturn(new SaTokenInfo());
	}

	@AfterEach
	void tearDown() {
		securityUtils.close();
		stpUtil.close();
		if (previousSaTokenConfig != null) {
			SaManager.setConfig(previousSaTokenConfig);
		}
	}

	private SysUser givenUser(String username, String password, String status) {
		SysUser sysUser = new SysUser();
		sysUser.setId("user-1");
		sysUser.setUsername(username);
		sysUser.setPassword(BCrypt.hashpw(password));
		sysUser.setStatus(status);
		sysUser.setTenantId("tenant-1");
		sysUser.setPermissions(Set.of("upms:sysuser:page"));
		when(remoteSysUserService.getUserInfo(username)).thenReturn(sysUser);
		return sysUser;
	}

	@Test
	void unknownAccountAndWrongPasswordShareSameMessage() {
		when(remoteSysUserService.getUserInfo("ghost")).thenReturn(null);
		assertThatThrownBy(() -> loginService.login("ghost", "whatever123"))
			.isInstanceOfSatisfying(ArynBusinessException.class,
				e -> assertThat(e.getMsg()).isEqualTo("账号或密码错误"));
		verify(loginAttemptGuard).recordFailure("ghost", null);

		givenUser("admin", STRONG_PASSWORD, "0");
		assertThatThrownBy(() -> loginService.login("admin", "WrongPass123"))
			.isInstanceOfSatisfying(ArynBusinessException.class,
				e -> assertThat(e.getMsg()).isEqualTo("账号或密码错误"));
		verify(loginAttemptGuard).recordFailure("admin", null);
	}

	@Test
	void lockedLoginIsRejectedBeforeQueryingUser() {
		when(loginAttemptGuard.retryAfterSeconds("admin", null)).thenReturn(300L);

		assertThatThrownBy(() -> loginService.login("admin", STRONG_PASSWORD))
			.isInstanceOfSatisfying(ArynBusinessException.class,
				e -> assertThat(e.getMsg()).contains("分钟后再试"));

		verify(remoteSysUserService, never()).getUserInfo(anyString());
	}

	@Test
	void successfulLoginClearsPasswordHashAndAppliesTobTimeout() {
		givenUser("admin", STRONG_PASSWORD, "0");
		properties.setTobTimeoutSeconds(3600L);

		loginService.login("admin", STRONG_PASSWORD);

		verify(loginAttemptGuard).resetAccount("admin");
		ArgumentCaptor<ArynUser> userCaptor = ArgumentCaptor.forClass(ArynUser.class);
		ArgumentCaptor<SaLoginParameter> parameterCaptor = ArgumentCaptor.forClass(SaLoginParameter.class);
		securityUtils.verify(() -> SecurityUtils.loginByDevice(userCaptor.capture(),
				eq(DeviceTypeEnum.TOB), parameterCaptor.capture()));
		assertThat(userCaptor.getValue().getPassword()).as("密码 hash 不得写入会话").isNull();
		assertThat(userCaptor.getValue().getUserId()).isEqualTo("user-1");
		assertThat(parameterCaptor.getValue().getTimeout()).isEqualTo(3600L);
		// 单点登录策略不能被登录入口覆盖
		assertThat(parameterCaptor.getValue().getIsConcurrent()).isFalse();
	}

	@Test
	void emptyOrInvalidHashLoginIsRejectedWithoutServerError() {
		givenUser("admin", STRONG_PASSWORD, "0");
		when(remoteSysUserService.getUserInfo("admin")).thenReturn(new SysUser());

		assertThatThrownBy(() -> loginService.login("admin", "Aryn2026Pass"))
			.isInstanceOfSatisfying(ArynBusinessException.class,
				e -> assertThat(e.getMsg()).isEqualTo("账号或密码错误"));
	}

	@Test
	void nonNormalStatusIsRejectedAfterPasswordCheck() {
		givenUser("admin", STRONG_PASSWORD, "1");

		assertThatThrownBy(() -> loginService.login("admin", STRONG_PASSWORD))
			.isInstanceOfSatisfying(ArynBusinessException.class,
				e -> assertThat(e.getMsg()).isEqualTo("状态异常不可登录"));
	}

	@Test
	void deliveryLoginIsProtectedBySameGuardAndClearsHash() {
		SysUser sysUser = new SysUser();
		sysUser.setId("user-2");
		sysUser.setUsername("rider");
		sysUser.setPassword(BCrypt.hashpw(STRONG_PASSWORD));
		sysUser.setStatus("0");
		sysUser.setTenantId("tenant-1");
		sysUser.setPermissions(Set.of("delivery:execute"));
		when(remoteSysUserService.getUserInfoByPhone("13800000000")).thenReturn(sysUser);

		loginService.deliveryLogin("13800000000", STRONG_PASSWORD);

		ArgumentCaptor<ArynUser> userCaptor = ArgumentCaptor.forClass(ArynUser.class);
		securityUtils.verify(() -> SecurityUtils.loginByDevice(userCaptor.capture(),
				eq(DeviceTypeEnum.TOB), any(SaLoginParameter.class)));
		assertThat(userCaptor.getValue().getPassword()).isNull();
		verify(loginAttemptGuard).resetAccount("13800000000");
	}

	@Test
	void deliveryLoginWithoutPermissionRejected() {
		SysUser sysUser = new SysUser();
		sysUser.setId("user-3");
		sysUser.setUsername("clerk");
		sysUser.setPassword(BCrypt.hashpw(STRONG_PASSWORD));
		sysUser.setStatus("0");
		sysUser.setTenantId("tenant-1");
		sysUser.setPermissions(Set.of("upms:sysuser:page"));
		when(remoteSysUserService.getUserInfoByPhone("13900000000")).thenReturn(sysUser);

		assertThatThrownBy(() -> loginService.deliveryLogin("13900000000", STRONG_PASSWORD))
			.isInstanceOfSatisfying(ArynBusinessException.class,
				e -> assertThat(e.getMsg()).isEqualTo("当前账号未开通配送权限"));
	}

}
