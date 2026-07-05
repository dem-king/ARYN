
package com.aryn.cloud.notify.listener;

import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.OrderCompleteEvent;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.notify.api.dto.NotifySendDTO;
import com.aryn.cloud.notify.service.INotifyMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 监听订单完成事件，发送站内信
 *
 * @author aryn
 * @since 2026/07/05
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = RocketMqConstants.ORDER_COMPLETE_NOTIFY_TOPIC,
		consumerGroup = "notify-order-complete-consumer-group")
public class NotifyOrderCompleteListener implements RocketMQListener<OrderCompleteEvent> {

	private final INotifyMessageService notifyMessageService;

	@Override
	public void onMessage(OrderCompleteEvent event) {
		log.info("收到订单完成事件，发送站内信: orderNo={}", event.getOrderNo());

		if (StrUtil.isNotBlank(event.getTenantId())) {
			ArynTenantContextHolder.setTenantId(event.getTenantId());
		}

		Map<String, String> params = new HashMap<>();
		params.put("orderNo", event.getOrderNo());
		params.put("orderId", event.getOrderId());

		NotifySendDTO dto = new NotifySendDTO()
				.setUserId(event.getUserId())
				.setTemplateCode("ORDER_COMPLETE")
				.setBizType("order")
				.setBizId(event.getOrderId())
				.setParams(params);

		notifyMessageService.sendMessage(dto);
	}

}
