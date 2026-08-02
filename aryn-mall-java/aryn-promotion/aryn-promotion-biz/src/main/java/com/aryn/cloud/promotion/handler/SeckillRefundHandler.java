package com.aryn.cloud.promotion.handler;

import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.promotion.api.entity.SeckillOrder;
import com.aryn.cloud.promotion.service.ISeckillOrderService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SeckillRefundHandler implements PromotionRefundEventHandler {

	private final ISeckillOrderService seckillOrderService;

	@Override
	public void handle(OrderRefundSuccessEvent event) {
		String orderId = event.getOrderId();
		long exists = seckillOrderService.count(Wrappers.<SeckillOrder>lambdaQuery()
				.eq(SeckillOrder::getOrderId, orderId));
		if (exists == 0) {
			return;
		}
		log.info("秒杀退款处理, orderId={}", orderId);
		seckillOrderService.handleRefundSuccess(orderId);
	}
}