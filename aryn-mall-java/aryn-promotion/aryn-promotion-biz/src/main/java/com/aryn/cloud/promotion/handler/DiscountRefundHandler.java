package com.aryn.cloud.promotion.handler;

import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.promotion.service.DiscountStockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 退款成功 → 回补折扣商品库存。
 *
 * <p>取消与超时已在下单侧 {@code releasePromotion} 释放；退款是另一条链路，
 * 走 RocketMQ 退款成功事件。释放按占用明细的 status 幂等，先取消后退款不会重复回补。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DiscountRefundHandler implements PromotionRefundEventHandler {

	private final DiscountStockService discountStockService;

	@Override
	public void handle(OrderRefundSuccessEvent event) {
		String orderId = event.getOrderId();
		if (!StringUtils.hasText(orderId)) {
			return;
		}
		int released = discountStockService.release(event.getTenantId(), orderId, "REFUND");
		if (released > 0) {
			log.info("折扣退款释放库存, orderId={}, rows={}", orderId, released);
		}
	}
}
