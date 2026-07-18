package com.aryn.cloud.user.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.user.api.vo.UserRespVO;
import com.aryn.cloud.user.controller.admin.UserInfoController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class UserInfoSecurityContractTest {

	@Test
	void everyAdminEndpointDeclaresPermission() {
		for (Method method : UserInfoController.class.getDeclaredMethods()) {
			if (isEndpoint(method)) {
				assertThat(method.getAnnotation(SaCheckPermission.class))
						.as("%s must declare @SaCheckPermission", method.getName())
						.isNotNull();
			}
		}
	}

	@Test
	void httpUserResponseNeverContainsPassword() {
		assertThat(Arrays.stream(UserRespVO.class.getDeclaredFields()).map(field -> field.getName()))
				.doesNotContain("password");
	}

	private boolean isEndpoint(Method method) {
		return method.isAnnotationPresent(GetMapping.class) || method.isAnnotationPresent(PostMapping.class)
				|| method.isAnnotationPresent(PutMapping.class) || method.isAnnotationPresent(DeleteMapping.class);
	}

}
