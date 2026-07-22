package com.aryn.cloud.message.listener;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.core.entity.OrderRefundSuccessEvent;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.service.MessageCommandService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 将退款成功事实转换为会员站内通知。 */
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = RocketMqConstants.ORDER_REFUND_SUCCESS_NOTIFY_TOPIC,
		consumerGroup = "message-order-refund-consumer")
public class OrderRefundMessageListener implements RocketMQListener<OrderRefundSuccessEvent> {

	private final MessageCommandService commandService;
	private final ObjectMapper objectMapper;

	@Override
	public void onMessage(OrderRefundSuccessEvent event) {
		ArynTenantContextHolder.removeTenantId();
		try {
			ArynTenantContextHolder.setTenantId(event.getTenantId());
			String refundId = event.getId() == null ? event.getRefundNo() : event.getId();
			MessageSendCommand command = new MessageSendCommand();
			command.setEventId("order-refunded:" + event.getRefundNo());
			command.setTenantId(event.getTenantId());
			command.setRecipientType(MessageIdentityType.MALL_USER.name());
			command.setRecipientId(event.getUserId());
			command.setCategory("REFUND");
			command.setTitle("退款已完成");
			command.setSummary("退款金额 " + event.getRefundAmount());
			command.setContent("您的退款已处理完成，到账时间以支付渠道为准。");
			command.setBizType("ORDER_REFUND");
			command.setBizId(refundId);
			command.setCardPayload(json(Map.of("refundId", refundId, "title", "退款申请",
					"amount", String.valueOf(event.getRefundAmount()))));
			command.setJumpPayload(json(Map.of("bizType", "REFUND", "bizId", refundId)));
			commandService.consume(command);
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}
	}

	private String json(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalStateException("退款通知序列化失败", exception);
		}
	}

}
