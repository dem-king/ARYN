package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.dto.MessageSendCommand;

/** 业务通知命令落库服务。 */
public interface MessageCommandService {

	void consume(MessageSendCommand command);

}
