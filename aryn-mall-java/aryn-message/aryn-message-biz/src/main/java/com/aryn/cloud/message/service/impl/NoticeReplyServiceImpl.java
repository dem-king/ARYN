package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.dto.conversation.ChatMessageSendRequest;
import com.aryn.cloud.message.api.entity.MessageNotice;
import com.aryn.cloud.message.api.entity.MessageRecipient;
import com.aryn.cloud.message.api.enums.ChatMessageType;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.enums.NoticeStatus;
import com.aryn.cloud.message.api.vo.conversation.ChatMessageVO;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;
import com.aryn.cloud.message.mapper.MessageNoticeMapper;
import com.aryn.cloud.message.mapper.MessageRecipientMapper;
import com.aryn.cloud.message.service.ChatMessageService;
import com.aryn.cloud.message.service.ConversationService;
import com.aryn.cloud.message.service.NoticeReplyService;
import com.aryn.cloud.message.service.StaffDirectService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 通知回复按身份隔离到客服会话或工作人员私信。 */
@Service
@RequiredArgsConstructor
public class NoticeReplyServiceImpl implements NoticeReplyService {

	private final MessageNoticeMapper noticeMapper;
	private final MessageRecipientMapper recipientMapper;
	private final ConversationService conversationService;
	private final StaffDirectService staffDirectService;
	private final ChatMessageService chatMessageService;
	private final ObjectMapper objectMapper;

	@Override
	public ChatMessageVO replyAsMember(String tenantId, String memberId, String memberName, String memberAvatar,
			String noticeId, String clientMessageId) {
		MessageNotice notice = requireRecipientNotice(tenantId, MessageIdentityType.MALL_USER, memberId, noticeId);
		ConversationVO conversation = conversationService.getOrCreateCustomerService(tenantId, memberId, memberName,
				memberAvatar, "DEFAULT", null);
		return chatMessageService.send(tenantId, MessageIdentityType.MALL_USER, memberId, memberName, memberAvatar,
				conversation.getId(), noticeCard(notice, clientMessageId));
	}

	@Override
	public ChatMessageVO replyAsStaff(String tenantId, String staffId, String staffName, String staffAvatar,
			String noticeId, String clientMessageId) {
		MessageNotice notice = requireRecipientNotice(tenantId, MessageIdentityType.SYS_USER, staffId, noticeId);
		if (!MessageIdentityType.SYS_USER.name().equals(notice.getSenderType()) || staffId.equals(notice.getSenderId())) {
			throw new ArynBusinessException("该通知没有可回复的工作人员发布人");
		}
		ConversationVO conversation = staffDirectService.getOrCreate(tenantId, staffId, staffName, staffAvatar,
				notice.getSenderId());
		return chatMessageService.send(tenantId, MessageIdentityType.SYS_USER, staffId, staffName, staffAvatar,
				conversation.getId(), noticeCard(notice, clientMessageId));
	}

	private MessageNotice requireRecipientNotice(String tenantId, MessageIdentityType recipientType, String recipientId,
			String noticeId) {
		MessageRecipient recipient = recipientMapper.selectRecipient(tenantId, recipientType.name(), recipientId, noticeId);
		if (recipient == null) {
			throw new ArynBusinessException("通知不存在或无权回复");
		}
		MessageNotice notice = noticeMapper.selectById(noticeId);
		if (notice == null || !tenantId.equals(notice.getTenantId())
				|| !NoticeStatus.PUBLISHED.name().equals(notice.getStatus())) {
			throw new ArynBusinessException("通知不存在或不可回复");
		}
		return notice;
	}

	private ChatMessageSendRequest noticeCard(MessageNotice notice, String clientMessageId) {
		ObjectNode payload = objectMapper.createObjectNode();
		payload.put("noticeId", notice.getId());
		payload.put("title", notice.getTitle());
		payload.put("summary", notice.getSummary());
		if (notice.getPublishTime() != null) {
			payload.put("publishTime", notice.getPublishTime().toString());
		}
		ChatMessageSendRequest request = new ChatMessageSendRequest();
		request.setClientMessageId(clientMessageId);
		request.setMessageType(ChatMessageType.NOTICE_CARD);
		try {
			request.setPayload(objectMapper.writeValueAsString(payload));
		}
		catch (JsonProcessingException exception) {
			throw new ArynBusinessException("通知卡片生成失败");
		}
		return request;
	}

}
