package com.aryn.cloud.message.service;

/** 消息提交后的轻量实时推送服务。 */
public interface MessagePushService {

	void pushConversationMessage(String tenantId, String conversationId, String messageId, long seqNo);

	/** 通知共享客服池：有未分配会话产生新消息，待领取列表需要刷新。 */
	void pushConversationQueued(String tenantId, String conversationId, String messageId, long seqNo);

	void pushNotice(String tenantId, String recipientType, String recipientId, String messageId);

}
