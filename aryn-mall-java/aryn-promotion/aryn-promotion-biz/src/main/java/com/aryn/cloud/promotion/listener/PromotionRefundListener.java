
package com.aryn.cloud.promotion.listener;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.handler.PromotionRefundEventHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 退款成功事件监听器-营销服务
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = RocketMqConstants.ORDER_REFUND_SUCCESS_NOTIFY_TOPIC,
		consumerGroup = "promotion-service-refund-group")
public class PromotionRefundListener implements RocketMQListener<OrderRefundSuccessEvent> {

	private final List<PromotionRefundEventHandler> handlers;

	@Override
	public void onMessage(OrderRefundSuccessEvent event) {
		try {
			ArynTenantContextHolder.setTenantId(event.getTenantId());
			log.info("退款成功事件监听器-营销服务收到消息：{}", event);
			for (PromotionRefundEventHandler handler : handlers) {
				handler.handle(event);
			}
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}
	}

}
