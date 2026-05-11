
package com.aryn.cloud.promotion.handler;

import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;

public interface PromotionRefundEventHandler {

	void handle(OrderRefundSuccessEvent event);

}
