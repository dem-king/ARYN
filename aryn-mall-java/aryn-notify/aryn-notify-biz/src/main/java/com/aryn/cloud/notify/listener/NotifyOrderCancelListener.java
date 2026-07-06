
package com.aryn.cloud.notify.listener;

import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.util.RocketMqConsumerHelper;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.notify.api.dto.NotifySendDTO;
import com.aryn.cloud.notify.service.INotifyMessageService;
import com.aryn.cloud.order.api.dto.OrderConsumerDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 监听订单取消事件（超时未支付自动取消），发送站内信
 *
 * @author aryn
 * @since 2026/07/05
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = RocketMqConstants.ORDER_CANCEL_TOPIC,
		consumerGroup = "notify-order-cancel-consumer-group",
		maxReconsumeTimes = RocketMqConstants.DEFAULT_MAX_RECONSUME_TIMES)
public class NotifyOrderCancelListener implements RocketMQListener<OrderConsumerDTO> {

	private final INotifyMessageService notifyMessageService;

	@Override
	public void onMessage(OrderConsumerDTO orderConsumerDTO) {
		RocketMqConsumerHelper.safeConsume(log, RocketMqConstants.ORDER_CANCEL_TOPIC,
				"notify-order-cancel-consumer-group", orderConsumerDTO, () -> doConsume(orderConsumerDTO));
	}

	private void doConsume(OrderConsumerDTO orderConsumerDTO) {
		// 设置租户上下文（MQ 消费侧无 HTTP 请求上下文）
		if (StrUtil.isNotBlank(orderConsumerDTO.getTenantId())) {
			ArynTenantContextHolder.setTenantId(orderConsumerDTO.getTenantId());
		}

		Map<String, String> params = new HashMap<>();
		params.put("orderNo", StrUtil.blankToDefault(orderConsumerDTO.getOrderNo(), orderConsumerDTO.getOrderId()));
		params.put("orderId", orderConsumerDTO.getOrderId());

		NotifySendDTO dto = new NotifySendDTO()
				.setUserId(orderConsumerDTO.getUserId())
				.setTemplateCode("ORDER_CANCEL")
				.setBizType("order")
				.setBizId(orderConsumerDTO.getOrderId())
				.setParams(params);

		notifyMessageService.sendMessage(dto);
	}

}
