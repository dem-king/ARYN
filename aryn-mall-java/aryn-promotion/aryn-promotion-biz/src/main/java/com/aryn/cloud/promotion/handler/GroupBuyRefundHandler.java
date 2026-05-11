package com.aryn.cloud.promotion.handler;

import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GroupBuyRefundHandler implements PromotionRefundEventHandler {

	@Override
	public void handle(OrderRefundSuccessEvent event) {
		log.info("拼团退款确认, orderId={}", event.getOrderId());
	}
}
