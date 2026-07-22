package com.aryn.cloud.auth.controller;

import com.aryn.cloud.auth.service.LoginService;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TobTokenControllerTest {

	@Test
	void loginPassesHttpsProtectedPasswordToLoginServiceWithoutSharedClientSecret() {
		LoginService loginService = mock(LoginService.class);
		TobTokenController controller = new TobTokenController(loginService);

		controller.login("admin", "plain-password");

		verify(loginService).login("admin", "plain-password");
	}

}
