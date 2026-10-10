package com.aryn.cloud.order.support;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.order.api.entity.OrderConfig;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCancelTimeoutHelperTest {

	@Test
	void missingOrInvalidConfigFallsBackToDefaultLevel() {
		assertThat(OrderCancelTimeoutHelper.resolveDelayLevel(null))
			.isEqualTo(RocketMqConstants.ORDER_CANCEL_LEVEL);
		assertThat(OrderCancelTimeoutHelper.resolveDelayLevel(new OrderConfig()))
			.isEqualTo(RocketMqConstants.ORDER_CANCEL_LEVEL);
		OrderConfig invalid = new OrderConfig();
		invalid.setOrderCancelTimeout("invalid");
		assertThat(OrderCancelTimeoutHelper.resolveDelayLevel(invalid))
			.isEqualTo(RocketMqConstants.ORDER_CANCEL_LEVEL);
	}

	@Test
	void configuredLevelOverridesDefault() {
		OrderConfig config = new OrderConfig();
		config.setOrderCancelTimeout("15");
		assertThat(OrderCancelTimeoutHelper.resolveDelayLevel(config)).isEqualTo(15);
	}

	@Test
	void delayLevelMapsToDictMinutes() {
		assertThat(OrderCancelTimeoutHelper.delayLevelToMinutes(9)).isEqualTo(5);
		assertThat(OrderCancelTimeoutHelper.delayLevelToMinutes(14)).isEqualTo(10);
		assertThat(OrderCancelTimeoutHelper.delayLevelToMinutes(15)).isEqualTo(20);
		assertThat(OrderCancelTimeoutHelper.delayLevelToMinutes(16)).isEqualTo(30);
		assertThat(OrderCancelTimeoutHelper.delayLevelToMinutes(17)).isEqualTo(60);
		assertThat(OrderCancelTimeoutHelper.delayLevelToMinutes(18)).isEqualTo(120);
	}

	@Test
	void secondsLevelAndUnknownLevelFallBackToDefaultMinutes() {
		int defaultMinutes = OrderCancelTimeoutHelper.delayLevelToMinutes(RocketMqConstants.ORDER_CANCEL_LEVEL);
		assertThat(defaultMinutes).isEqualTo(30);
		assertThat(OrderCancelTimeoutHelper.delayLevelToMinutes(4)).isEqualTo(defaultMinutes);
		assertThat(OrderCancelTimeoutHelper.delayLevelToMinutes(0)).isEqualTo(defaultMinutes);
		assertThat(OrderCancelTimeoutHelper.delayLevelToMinutes(99)).isEqualTo(defaultMinutes);
	}

}
