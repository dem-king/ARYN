package com.aryn.cloud.user.controller.app;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 推广码接口契约：user 域 `/app/sales/qrcode`（cloud 下为 /mall-user/app/sales/qrcode）。
 */
class AppSalesQrcodeControllerContractTest {

	@Test
	void qrcodeEndpointMatchesPlan() {
		assertThat(AppSalesQrcodeController.class.getAnnotation(RequestMapping.class).value())
			.containsExactly("/app/sales");
		Method qrcode = Arrays.stream(AppSalesQrcodeController.class.getDeclaredMethods())
			.filter(method -> method.getName().equals("qrcode"))
			.findFirst()
			.orElseThrow();
		assertThat(qrcode.getAnnotation(GetMapping.class).value()).containsExactly("/qrcode");
	}

}
