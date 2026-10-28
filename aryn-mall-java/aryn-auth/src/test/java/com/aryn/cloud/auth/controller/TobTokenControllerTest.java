package com.aryn.cloud.auth.controller;

import com.aryn.cloud.auth.service.LoginService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TobTokenControllerTest {

	@Test
	void loginPassesHttpsProtectedPasswordToLoginServiceWithoutSharedClientSecret() {
		LoginService loginService = mock(LoginService.class);
		TobTokenController controller = new TobTokenController(loginService);

		controller.login("admin", "any-password-123");

		verify(loginService).login("admin", "any-password-123");
	}

	/**
	 * 密码/短信登录只接受 POST：历史实现使用不限方法的 {@code @RequestMapping}，
	 * 允许通过 GET 查询参数提交凭证，密码会进入 URL、代理与访问日志。
	 */
	@Test
	void passwordAndSmsLoginOnlyAcceptPost() throws NoSuchMethodException {
		Method login = TobTokenController.class.getMethod("login", String.class, String.class);
		assertThat(login.isAnnotationPresent(PostMapping.class)).as("密码登录必须限定 POST").isTrue();
		assertThat(login.isAnnotationPresent(RequestMapping.class)).isFalse();

		Method smsLogin = TobTokenController.class.getMethod("smsLogin", String.class);
		assertThat(smsLogin.isAnnotationPresent(PostMapping.class)).as("短信登录必须限定 POST").isTrue();
		assertThat(smsLogin.isAnnotationPresent(RequestMapping.class)).isFalse();
	}

	@Test
	void logoutKeepsDeleteSemantics() throws NoSuchMethodException {
		Method logout = TobTokenController.class.getMethod("logout");
		assertThat(logout.isAnnotationPresent(DeleteMapping.class)).isTrue();
	}

}
