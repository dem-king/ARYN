package com.aryn.cloud.message.service;

/** 消息提交后的轻量实时推送服务。 */
public interface MessagePushService {

	void pushConversationMessage(String tenantId, String conversationId, String messageId, long seqNo);

	void pushNotice(String tenantId, String recipientType, String recipientId, String messageId);

}
