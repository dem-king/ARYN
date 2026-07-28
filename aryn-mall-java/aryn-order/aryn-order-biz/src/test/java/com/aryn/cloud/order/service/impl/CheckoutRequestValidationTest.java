package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.order.api.dto.CreateOrderDTO;
import com.aryn.cloud.order.api.dto.CreateOrderSkuReqDTO;
import com.aryn.cloud.order.api.dto.OrderAppraiseDTO;
import com.aryn.cloud.order.api.dto.SettlementOrderDTO;
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

	@Test
	void checkoutAcceptsMallDeliveryAndRejectsUnknownDeliveryWays() {
		CreateOrderSkuReqDTO sku = new CreateOrderSkuReqDTO();
		sku.setSkuId("sku-1");
		sku.setQuantity(1);
		CreateOrderDTO create = new CreateOrderDTO();
		create.setDeliveryWay("3");
		create.setCreateWay("2");
		create.setSkuReqList(List.of(sku));
		SettlementOrderDTO settlement = new SettlementOrderDTO();
		settlement.setDeliveryWay("3");
		settlement.setCreateWay("2");
		settlement.setSkuReqList(List.of(sku));

		assertThat(validator.validate(create)).isEmpty();
		assertThat(validator.validate(settlement)).isEmpty();

		create.setDeliveryWay("4");
		settlement.setDeliveryWay("0");
		assertThat(validator.validate(create))
				.extracting(violation -> violation.getPropertyPath().toString())
				.contains("deliveryWay");
		assertThat(validator.validate(settlement))
				.extracting(violation -> violation.getPropertyPath().toString())
				.contains("deliveryWay");
	}
}
