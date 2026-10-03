package com.aryn.cloud.promotion.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.promotion.controller.admin.PageDesignController;
import com.aryn.cloud.promotion.controller.admin.PageDesignTemplateController;
import com.aryn.cloud.promotion.controller.app.AppPageDesignController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
				"promotion:pagedesign:rollback", "setHome", "promotion:pagedesign:publish");
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

	@Test
	void marketListAcceptsTheFiltersTheAdminPageSends() {
		Map<String, RequestParam> params = Arrays.stream(marketListMethod().getParameters())
			.filter(parameter -> parameter.isAnnotationPresent(RequestParam.class))
			.collect(java.util.stream.Collectors.toMap(this::paramName,
					parameter -> parameter.getAnnotation(RequestParam.class)));
		// 管理端模板市场页按这几个筛选/排序参数发起请求，漏接会静默忽略筛选条件
		assertTrue(params.containsKey("templateName"), () -> "marketList 未接收 templateName，名称搜索会被静默忽略");
		assertTrue(params.containsKey("industryTag"));
		assertTrue(params.containsKey("pageNum"));
		assertTrue(params.containsKey("pageSize"));
		assertEquals("downloadCount", params.get("sortField").defaultValue());
	}

	private String paramName(java.lang.reflect.Parameter parameter) {
		String declared = parameter.getAnnotation(RequestParam.class).value();
		return declared.isEmpty() ? parameter.getName() : declared;
	}

	private Method marketListMethod() {
		return Arrays.stream(PageDesignTemplateController.class.getDeclaredMethods())
			.filter(method -> method.getName().equals("marketList"))
			.findFirst()
			.orElseThrow();
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
