package com.aryn.cloud.promotion.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.promotion.controller.admin.PromotionDistributionCouponExchangeController;
import com.aryn.cloud.promotion.controller.admin.PromotionDistributionCouponPackController;
import com.aryn.cloud.promotion.controller.app.AppDistributionController;
import com.aryn.cloud.promotion.controller.app.AppDistributionCouponController;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 券包/券兑换/提现下线的接口契约（路径与权限点），守住前端已按 IMPLEMENTATION_PLAN 3.2/3.3 对接的路径。
 */
class PromotionDistributionCouponContractTest {

	@Test
	void adminCouponPackEndpointsMatchPlan() {
		assertThat(PromotionDistributionCouponPackController.class.getAnnotation(RequestMapping.class).value())
			.containsExactly("/distribution/coupon/pack");
		assertMapping(PromotionDistributionCouponPackController.class, "page", GetMapping.class, "/page",
			"promotion:distributioncouponpack:page");
		assertMapping(PromotionDistributionCouponPackController.class, "list", GetMapping.class, "/list",
			"promotion:distributioncouponpack:page");
		assertMapping(PromotionDistributionCouponPackController.class, "get", GetMapping.class, "/{id}",
			"promotion:distributioncouponpack:page");
		assertMapping(PromotionDistributionCouponPackController.class, "add", PostMapping.class, "/add",
			"promotion:distributioncouponpack:add");
		assertMapping(PromotionDistributionCouponPackController.class, "edit", PutMapping.class, "/edit",
			"promotion:distributioncouponpack:edit");
		assertMapping(PromotionDistributionCouponPackController.class, "status", PostMapping.class, "/status",
			"promotion:distributioncouponpack:edit");
		assertMapping(PromotionDistributionCouponPackController.class, "remove", DeleteMapping.class, "/{id}",
			"promotion:distributioncouponpack:del");
	}

	@Test
	void adminCouponExchangeEndpointsMatchPlan() {
		assertThat(PromotionDistributionCouponExchangeController.class.getAnnotation(RequestMapping.class).value())
			.containsExactly("/distribution/coupon/exchange");
		assertMapping(PromotionDistributionCouponExchangeController.class, "page", GetMapping.class, "/page",
			"promotion:distributioncouponexchange:page");
		assertMapping(PromotionDistributionCouponExchangeController.class, "statistics", GetMapping.class,
			"/statistics", "promotion:distributioncouponexchange:page");
	}

	@Test
	void appCouponEndpointsMatchPlan() {
		assertThat(AppDistributionCouponController.class.getAnnotation(RequestMapping.class).value())
			.containsExactly("/app/distribution/coupon");
		assertThat(getMethod(AppDistributionCouponController.class, "packList").getAnnotation(GetMapping.class)
			.value()).containsExactly("/pack/list");
		assertThat(getMethod(AppDistributionCouponController.class, "exchange").getAnnotation(PostMapping.class)
			.value()).containsExactly("/exchange");
		assertThat(getMethod(AppDistributionCouponController.class, "exchangePage").getAnnotation(GetMapping.class)
			.value()).containsExactly("/exchange/page");
	}

	@Test
	void withdrawApplyEndpointKeptForExplicitOfflineError() {
		assertThat(AppDistributionController.class.getAnnotation(RequestMapping.class).value())
			.containsExactly("/app/distribution");
		Method apply = getMethod(AppDistributionController.class, "apply");
		assertThat(apply.getAnnotation(PostMapping.class).value()).containsExactly("/withdraw/apply");
		assertThat(apply.getAnnotation(SaCheckPermission.class)).isNull();
	}

	private void assertMapping(Class<?> controller, String methodName,
			Class<? extends java.lang.annotation.Annotation> mappingType, String path, String permission) {
		Method method = getMethod(controller, methodName);
		java.lang.annotation.Annotation mapping = method.getAnnotation(mappingType);
		assertThat(mapping).as(controller.getSimpleName() + "#" + methodName + " 缺少 " + mappingType.getSimpleName())
			.isNotNull();
		try {
			Method valueMethod = mappingType.getMethod("value");
			assertThat((String[]) valueMethod.invoke(mapping)).containsExactly(path);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
		SaCheckPermission check = method.getAnnotation(SaCheckPermission.class);
		assertThat(check).as(controller.getSimpleName() + "#" + methodName + " 缺少权限点").isNotNull();
		assertThat(check.value()).containsExactly(permission);
	}

	private Method getMethod(Class<?> controller, String methodName) {
		return Arrays.stream(controller.getDeclaredMethods())
			.filter(method -> method.getName().equals(methodName))
			.findFirst()
			.orElseThrow(() -> new AssertionError("方法不存在：" + controller.getSimpleName() + "#" + methodName));
	}

}
