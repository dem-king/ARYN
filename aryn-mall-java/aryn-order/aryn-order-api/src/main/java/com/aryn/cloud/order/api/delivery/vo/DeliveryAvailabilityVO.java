package com.aryn.cloud.order.api.delivery.vo;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 商城配送可用性。
 */
@Data
@Accessors(chain = true)
public class DeliveryAvailabilityVO {

	private boolean available;

	private String matchedScopeLevel;

	private String reason;

}
