package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.order.api.dto.CreateOrderDTO;
import com.aryn.cloud.order.api.dto.CreateOrderSkuReqDTO;
import com.aryn.cloud.order.api.dto.OrderAppraiseDTO;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutRequestValidationTest {

	private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

	@Test
	void createOrderRequiresDeliveryWayCreateWayAndPositiveQuantity() {
		CreateOrderSkuReqDTO sku = new CreateOrderSkuReqDTO();
		sku.setSkuId("sku-1");
		sku.setQuantity(0);
		CreateOrderDTO request = new CreateOrderDTO();
		request.setSkuReqList(List.of(sku));

		assertThat(validator.validate(request))
			.extracting(violation -> violation.getPropertyPath().toString())
			.contains("deliveryWay", "createWay", "skuReqList[0].quantity");
	}

	@Test
	void appraiseRequiresOrderItemScoreAndContent() {
		OrderAppraiseDTO request = new OrderAppraiseDTO();
		request.setGoodsScore(6);

		assertThat(validator.validate(request))
			.extracting(violation -> violation.getPropertyPath().toString())
			.contains("orderItemId", "goodsScore", "content");
	}
}
