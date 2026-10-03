package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.dto.conversation.ConversationInboxQuery;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ConversationAttentionVO;
import com.aryn.cloud.message.api.vo.conversation.ConversationInboxPageVO;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;

/** 会话创建、查询和已读服务。 */
public interface ConversationService {

	ConversationVO getOrCreateCustomerService(String tenantId, String customerId, String customerName,
			String customerAvatar, String queueCode, String contextPayload);

	ConversationVO initiateCustomerService(String tenantId, String staffId, String customerId, String queueCode,
			String contextPayload);

	ConversationVO get(String tenantId, MessageIdentityType identityType, String identityId, String conversationId);

	ConversationInboxPageVO inbox(String tenantId, MessageIdentityType identityType, String identityId,
			ConversationInboxQuery query);

	/**
	 * 工作人员待办提醒：本人未读会话与共享池待领取会话。
	 *
	 * <p>共享池只在调用方是启用中的客服坐席时才计入，避免普通管理员
	 * 看到自己无权领取的会话。
	 */
	ConversationAttentionVO attention(String tenantId, MessageIdentityType identityType, String identityId,
			String queueCode, int limit);

	void markRead(String tenantId, MessageIdentityType identityType, String identityId, String conversationId,
			long lastReadSeq);

}
