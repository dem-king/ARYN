package com.aryn.cloud.promotion.controller.admin;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class DistributionOrderControllerContractTest {

	@Test
	void manualSettlementAcceptsOnlyDistributionOrderId() {
		Method settle = java.util.Arrays.stream(DistributionOrderController.class.getMethods())
			.filter(method -> method.getName().equals("settle"))
			.findFirst()
			.orElseThrow();

		assertThat(settle.getParameterTypes()).containsExactly(String.class);
	}
}
