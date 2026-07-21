package com.aryn.cloud.product.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.product.api.entity.GoodsSpecs;
import com.aryn.cloud.product.api.entity.GoodsSpecsValue;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ProductAdminPermissionTest {

	@Test
	void specificationCreateEndpointsRequireCreatePermissions() throws NoSuchMethodException {
		assertPermission(GoodsSpecsController.class.getDeclaredMethod("add", GoodsSpecs.class),
				"product:goodsspecs:add");
		assertPermission(GoodsSpecsValueController.class.getDeclaredMethod("add", GoodsSpecsValue.class),
				"product:goodsspecsvalue:add");
	}

	@Test
	void productStatusAndCountUseExistingProductPermissions() throws NoSuchMethodException {
		assertPermission(GoodsSpuController.class.getDeclaredMethod("updateStatus", GoodsSpu.class),
				"product:goodsspu:edit");
		assertPermission(GoodsSpuController.class.getDeclaredMethod("count"), "product:goodsspu:page");
	}

	private void assertPermission(Method method, String permission) {
		SaCheckPermission annotation = method.getAnnotation(SaCheckPermission.class);
		assertThat(annotation).as("%s should declare SaCheckPermission", method).isNotNull();
		assertThat(annotation.value()).contains(permission);
	}

}
