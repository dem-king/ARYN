package com.aryn.cloud.promotion.handler;

import com.aryn.cloud.common.core.entity.OrderPaySuccessEvent;
import com.aryn.cloud.promotion.api.entity.SeckillOrder;
import com.aryn.cloud.promotion.service.ISeckillOrderService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillPayHandler implements PromotionPayEventHandler {

	private final ISeckillOrderService seckillOrderService;

	@Override
	public void handle(OrderPaySuccessEvent event) {
		String orderId = event.getOrderId();
		long exists = seckillOrderService.count(Wrappers.<SeckillOrder>lambdaQuery()
				.eq(SeckillOrder::getOrderId, orderId));
		if (exists == 0) {
			return;
		}
		log.info("秒杀支付成功处理, orderId={}", orderId);
		seckillOrderService.handlePaySuccess(orderId);
	}
}