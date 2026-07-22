package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.dto.conversation.ChatMessageCursorQuery;
import com.aryn.cloud.message.api.dto.conversation.ChatMessageSendRequest;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ChatMessagePageVO;
import com.aryn.cloud.message.api.vo.conversation.ChatMessageVO;

/** 会话消息可靠发送与游标读取服务。 */
public interface ChatMessageService {

	ChatMessageVO send(String tenantId, MessageIdentityType senderType, String senderId, String senderName,
			String senderAvatar, String conversationId, ChatMessageSendRequest request);

	ChatMessageVO sendSystem(String tenantId, String conversationId, String content, String payload,
			String clientMessageId);

	ChatMessagePageVO page(String tenantId, MessageIdentityType identityType, String identityId,
			String conversationId, ChatMessageCursorQuery query);

}
