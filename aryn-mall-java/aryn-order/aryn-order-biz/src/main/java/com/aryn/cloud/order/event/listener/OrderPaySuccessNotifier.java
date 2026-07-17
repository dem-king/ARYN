package com.aryn.cloud.order.event.listener;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.OrderItemPaySuccessEvent;
import com.aryn.cloud.common.core.entity.OrderPaySuccessEvent;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.api.entity.OrderItemEntity;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.BeanUtils;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderPaySuccessNotifier {

	private final RocketMQTemplate rocketMQTemplate;

	public void notify(OrderInfo orderInfo, List<OrderItemEntity> orderItems) {
		OrderPaySuccessEvent event = new OrderPaySuccessEvent();
		BeanUtils.copyProperties(orderInfo, event);
		event.setOrderId(orderInfo.getId());
		event.setItemList(orderItems.stream().map(item -> {
			OrderItemPaySuccessEvent itemEvent = new OrderItemPaySuccessEvent();
			BeanUtils.copyProperties(item, itemEvent);
			itemEvent.setOrderId(orderInfo.getId());
			return itemEvent;
		}).toList());

		rocketMQTemplate.syncSend(RocketMqConstants.ORDER_PAY_SUCCESS_NOTIFY_TOPIC,
			new GenericMessage<>(event), RocketMqConstants.TIME_OUT);
	}

}
