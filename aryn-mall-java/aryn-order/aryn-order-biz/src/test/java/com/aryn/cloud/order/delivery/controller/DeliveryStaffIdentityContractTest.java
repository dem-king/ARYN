package com.aryn.cloud.order.delivery.controller;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryStaffIdentityContractTest {

	@Test
	void everyStaffEndpointExplicitlyRequiresTobIdentity() throws Exception {
		Path source = Path.of("src/main/java/com/aryn/cloud/order/delivery/controller/staff/DeliveryTaskStaffController.java");
		String controller = Files.readString(source);

		assertThat(controller).contains("SecurityUtils.requireUser(DeviceTypeEnum.TOB)");
		assertThat(controller).contains("@RequestMapping(\"/delivery/staff\")");
		assertThat(controller).contains("@SaCheckPermission(\"order:delivery:execute\")");
	}

}
