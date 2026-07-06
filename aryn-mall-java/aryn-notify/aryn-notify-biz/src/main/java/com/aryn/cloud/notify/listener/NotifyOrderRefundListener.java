
package com.aryn.cloud.notify.listener;

import cn.hutool.core.util.StrUtil;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.common.core.util.RocketMqConsumerHelper;
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
 * 监听订单退款成功事件，发送站内信
 *
 * @author aryn
 * @since 2026/07/05
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = RocketMqConstants.ORDER_REFUND_SUCCESS_NOTIFY_TOPIC,
		consumerGroup = "notify-order-refund-consumer-group",
		maxReconsumeTimes = RocketMqConstants.DEFAULT_MAX_RECONSUME_TIMES)
public class NotifyOrderRefundListener implements RocketMQListener<OrderRefundSuccessEvent> {

	private final INotifyMessageService notifyMessageService;

	@Override
	public void onMessage(OrderRefundSuccessEvent event) {
		RocketMqConsumerHelper.safeConsume(log, RocketMqConstants.ORDER_REFUND_SUCCESS_NOTIFY_TOPIC,
				"notify-order-refund-consumer-group", event, () -> doConsume(event));
	}

	private void doConsume(OrderRefundSuccessEvent event) {
		if (StrUtil.isNotBlank(event.getTenantId())) {
			ArynTenantContextHolder.setTenantId(event.getTenantId());
		}

		Map<String, String> params = new HashMap<>();
		params.put("orderNo", event.getOrderNo() == null ? event.getOrderId() : event.getOrderNo());
		params.put("amount", event.getRefundAmount() == null ? "0" : event.getRefundAmount().toPlainString());
		params.put("orderId", event.getOrderId());

		NotifySendDTO dto = new NotifySendDTO()
				.setUserId(event.getUserId())
				.setTemplateCode("REFUND_SUCCESS")
				.setBizType("refund")
				.setBizId(event.getRefundId())
				.setParams(params);

		notifyMessageService.sendMessage(dto);
	}

}
