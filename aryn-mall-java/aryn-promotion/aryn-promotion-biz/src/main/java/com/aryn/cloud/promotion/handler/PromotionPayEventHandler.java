
package com.aryn.cloud.promotion.handler;

import com.aryn.cloud.common.core.entity.OrderPaySuccessEvent;

public interface PromotionPayEventHandler {

	void handle(OrderPaySuccessEvent event);

}
