
package com.aryn.cloud.promotion.listener;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.OrderPaySuccessEvent;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.handler.PromotionPayEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 支付成功事件监听器-营销服务
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = RocketMqConstants.ORDER_PAY_SUCCESS_NOTIFY_TOPIC,
		consumerGroup = "promotion-service-pay-group")
public class PromotionPayListener implements RocketMQListener<OrderPaySuccessEvent> {

	private final List<PromotionPayEventHandler> handlers;

	@Override
	public void onMessage(OrderPaySuccessEvent orderPaySuccessEvent) {
		try {
			ArynTenantContextHolder.setTenantId(orderPaySuccessEvent.getTenantId());
			log.info("支付成功事件监听器-营销服务收到消息：{}", orderPaySuccessEvent);
			for (PromotionPayEventHandler handler : handlers) {
				handler.handle(orderPaySuccessEvent);
			}
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}
	}

}
