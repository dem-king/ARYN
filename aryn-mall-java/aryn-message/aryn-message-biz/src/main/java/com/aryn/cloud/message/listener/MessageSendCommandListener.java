package com.aryn.cloud.message.listener;

import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.service.MessageCommandService;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/** 统一业务通知命令消费者。 */
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC,
		consumerGroup = "message-send-command-consumer")
public class MessageSendCommandListener implements RocketMQListener<MessageSendCommand> {

	private final MessageCommandService commandService;

	@Override
	public void onMessage(MessageSendCommand command) {
		ArynTenantContextHolder.removeTenantId();
		try {
			ArynTenantContextHolder.setTenantId(command.getTenantId());
			commandService.consume(command);
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}
	}

}
