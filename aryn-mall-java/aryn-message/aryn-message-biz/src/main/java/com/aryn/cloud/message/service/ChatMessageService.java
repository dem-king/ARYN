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

	/**
	 * 撤回本人发送的消息（管理端客服工作台/私信页）。
	 *
	 * <p>规则：调用者为会话 ACTIVE 参与者且是消息发送者本人；会话未关闭；仅 TEXT/IMAGE；
	 * 发送后 2 分钟内；已撤回再次调用按幂等成功返回。撤回后 content/payload 不再下发（DB 保留审计），
	 * 若撤回的是会话最后一条消息，同步把会话摘要改为「[消息已撤回]」，并复用
	 * CONVERSATION_MESSAGE 推送通知各端按游标补拉。</p>
	 */
	ChatMessageVO recall(String tenantId, MessageIdentityType senderType, String senderId, String conversationId,
			String messageId);

}
