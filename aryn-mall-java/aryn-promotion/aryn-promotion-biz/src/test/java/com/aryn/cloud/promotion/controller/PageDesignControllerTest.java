package com.aryn.cloud.promotion.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.promotion.controller.admin.PageDesignController;
import com.aryn.cloud.promotion.controller.admin.PageDesignTemplateController;
import com.aryn.cloud.promotion.controller.app.AppPageDesignController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PageDesignControllerTest {

	@Test
	void everyAdminEndpointDeclaresAnExplicitPermission() {
		assertEveryEndpointProtected(PageDesignController.class);
		assertEveryEndpointProtected(PageDesignTemplateController.class);
	}

	@Test
	void lifecycleActionsUseDedicatedPermissions() {
		Map<String, String> permissions = Map.of("saveDraft", "promotion:pagedesign:edit", "publish",
				"promotion:pagedesign:publish", "unpublish", "promotion:pagedesign:publish", "rollback",
				"promotion:pagedesign:rollback");
		permissions.forEach((method, permission) -> assertPermission(PageDesignController.class, method, permission));
		for (String method : new String[] { "add", "edit", "remove" }) {
			assertPermission(PageDesignTemplateController.class, method, "promotion:pagedesign:template");
		}
	}

	@Test
	void appPublishedAndPreviewReadsRemainPublic() {
		assertFalse(Arrays.stream(AppPageDesignController.class.getDeclaredMethods())
			.filter(this::isEndpoint)
			.anyMatch(method -> method.isAnnotationPresent(SaCheckPermission.class)));
	}

	private void assertEveryEndpointProtected(Class<?> controller) {
		for (Method method : controller.getDeclaredMethods()) {
			if (isEndpoint(method)) {
				assertNotNull(method.getAnnotation(SaCheckPermission.class),
						() -> controller.getSimpleName() + "." + method.getName() + " has no permission");
			}
		}
	}

	private void assertPermission(Class<?> controller, String methodName, String permission) {
		Method method = Arrays.stream(controller.getDeclaredMethods())
			.filter(candidate -> candidate.getName().equals(methodName))
			.findFirst()
			.orElseThrow();
		SaCheckPermission annotation = method.getAnnotation(SaCheckPermission.class);
		assertNotNull(annotation);
		assertArrayEquals(new String[] { permission }, annotation.value());
	}

	private boolean isEndpoint(Method method) {
		return Arrays.stream(method.getAnnotations())
			.map(annotation -> annotation.annotationType())
			.anyMatch(type -> type.isAnnotationPresent(RequestMapping.class));
	}

}
