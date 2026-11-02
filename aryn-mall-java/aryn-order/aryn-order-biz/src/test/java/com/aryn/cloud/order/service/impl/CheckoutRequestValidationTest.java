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
	void appraiseRequiresOrderItemAndValidScoresButContentIsOptional() {
		OrderAppraiseDTO request = new OrderAppraiseDTO();
		request.setGoodsScore(6);
		request.setLogisticsScore(0);
		request.setServiceScore(6);
		// 文字评价选填：不再要求 content 非空，但超过 255 字仍要拦截
		request.setContent("很好".repeat(200));
		request.setTags(List.of("太长了太长了太长了太长了太长了太长了"));

		assertThat(validator.validate(request))
			.extracting(violation -> violation.getPropertyPath().toString())
			.contains("orderItemId", "goodsScore", "logisticsScore", "serviceScore", "content")
			.anySatisfy(path -> assertThat(path).startsWith("tags[0]"));
	}

	@Test
	void appraiseAllowsEmptyContentAndValidDimensions() {
		OrderAppraiseDTO request = new OrderAppraiseDTO();
		request.setOrderItemId("item-1");
		request.setGoodsScore(5);
		request.setContent("   ");
		request.setLogisticsScore(5);
		request.setServiceScore(4);
		request.setTags(List.of("新鲜", "包装好"));
		request.setIsAnonymous(true);

		assertThat(validator.validate(request)).isEmpty();
	}

}
