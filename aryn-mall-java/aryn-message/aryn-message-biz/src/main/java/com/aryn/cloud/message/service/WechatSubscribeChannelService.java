package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.dto.MessageSendCommand;

/**
 * 微信小程序订阅消息通道。
 * 在 MessageCommandServiceImpl 中根据 channels 列表决定是否调用。
 */
public interface WechatSubscribeChannelService {

	/**
	 * 发送微信订阅消息。
	 * @param command 业务通知命令
	 */
	void send(MessageSendCommand command);
}