package com.aryn.cloud.order.delivery.service;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.order.api.delivery.entity.OrderDeliveryTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Set;

/** 派单提交后向配送员工发送站内信和微信订阅消息命令。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeliveryAssignmentNotifier {

	private final RocketMQTemplate rocketMQTemplate;

	@Value("${aryn.delivery.mini-app-id:delivery-app}")
	private String miniAppId;

	@Value("${aryn.delivery.assignment-template-code:delivery-assigned}")
	private String assignmentTemplateCode;

	public void notifyAssigned(OrderDeliveryTask task, String recipientId, String recipientName, int attemptNo) {
		MessageSendCommand command = new MessageSendCommand();
		command.setEventId("delivery-assigned:" + task.getId() + ":" + attemptNo);
		command.setTenantId(task.getTenantId());
		command.setRecipientType(MessageIdentityType.SYS_USER.name());
		command.setRecipientId(recipientId);
		command.setRecipientName(recipientName);
		command.setCategory("DELIVERY");
		command.setTitle("新的商城配送任务");
		command.setSummary("订单 " + task.getOrderNo() + " 等待配送");
		command.setContent("请及时进入配送工作台处理商城配送任务。");
		command.setBizType("DELIVERY_ASSIGNMENT");
		command.setBizId(task.getId());
		command.setChannels(Set.of("IN_APP", "WECHAT_SUBSCRIBE"));
		command.setMiniAppId(miniAppId);
		command.setTemplateCode(assignmentTemplateCode);
		runAfterCommit(() -> send(command));
	}

	private void send(MessageSendCommand command) {
		try {
			rocketMQTemplate.syncSend(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC,
				new GenericMessage<>(command), RocketMqConstants.TIME_OUT);
		}
		catch (RuntimeException exception) {
			log.error("配送派单通知发送失败，不回滚派单 tenantId={}, eventId={}, recipientId={}",
				command.getTenantId(), command.getEventId(), command.getRecipientId(), exception);
		}
	}

	private void runAfterCommit(Runnable action) {
		if (!TransactionSynchronizationManager.isSynchronizationActive()) {
			action.run();
			return;
		}
		TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
			@Override
			public void afterCommit() {
				action.run();
			}
		});
	}
}
