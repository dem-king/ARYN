package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.dto.conversation.ChatMessageCursorQuery;
import com.aryn.cloud.message.api.dto.conversation.ChatMessageSendRequest;
import com.aryn.cloud.message.api.entity.MessageChat;
import com.aryn.cloud.message.api.entity.MessageConversation;
import com.aryn.cloud.message.api.entity.MessageParticipant;
import com.aryn.cloud.message.api.enums.ChatMessageType;
import com.aryn.cloud.message.api.enums.ConversationStatus;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ChatMessagePageVO;
import com.aryn.cloud.message.api.vo.conversation.ChatMessageVO;
import com.aryn.cloud.message.mapper.MessageChatMapper;
import com.aryn.cloud.message.mapper.MessageConversationMapper;
import com.aryn.cloud.message.mapper.MessageParticipantMapper;
import com.aryn.cloud.message.service.ChatMessageService;
import com.aryn.cloud.message.service.MessagePushService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** 会话消息幂等、排序和游标读取实现。 */
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {

	private static final int DEFAULT_LIMIT = 30;
	private static final int MAX_LIMIT = 100;
	private static final Set<ChatMessageType> CLIENT_TYPES = Set.of(ChatMessageType.TEXT, ChatMessageType.IMAGE,
			ChatMessageType.PRODUCT_CARD, ChatMessageType.ORDER_CARD, ChatMessageType.REFUND_CARD,
			ChatMessageType.NOTICE_CARD);

	private final MessageConversationMapper conversationMapper;
	private final MessageParticipantMapper participantMapper;
	private final MessageChatMapper chatMapper;
	private final ObjectMapper objectMapper;
	private final MessagePushService pushService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ChatMessageVO send(String tenantId, MessageIdentityType senderType, String senderId, String senderName,
			String senderAvatar, String conversationId, ChatMessageSendRequest request) {
		validateClientMessage(request);
		MessageChat existing = chatMapper.selectByClientMessageId(tenantId, senderType.name(), senderId,
				request.getClientMessageId());
		if (existing != null) {
			assertSameConversation(existing, conversationId);
			return toVO(existing);
		}
		MessageConversation conversation = requireWritableConversation(tenantId, conversationId, senderType, senderId);
		existing = chatMapper.selectByClientMessageId(tenantId, senderType.name(), senderId,
				request.getClientMessageId());
		if (existing != null) {
			assertSameConversation(existing, conversationId);
			return toVO(existing);
		}
		return append(conversation, senderType, senderId, senderName, senderAvatar, request.getMessageType(),
				request.getContent(), request.getPayload(), request.getClientMessageId(), request.getQuotedMessageId());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ChatMessageVO sendSystem(String tenantId, String conversationId, String content, String payload,
			String clientMessageId) {
		validatePayload(payload, ChatMessageType.SYSTEM);
		MessageChat existing = chatMapper.selectByClientMessageId(tenantId, MessageIdentityType.SYSTEM.name(), "SYSTEM",
				clientMessageId);
		if (existing != null) {
			assertSameConversation(existing, conversationId);
			return toVO(existing);
		}
		MessageConversation conversation = conversationMapper.selectByIdForUpdate(tenantId, conversationId);
		if (conversation == null) {
			throw new ArynBusinessException("会话不存在");
		}
		return append(conversation, MessageIdentityType.SYSTEM, "SYSTEM", "系统", null, ChatMessageType.SYSTEM,
				content, payload, clientMessageId, null);
	}

	@Override
	public ChatMessagePageVO page(String tenantId, MessageIdentityType identityType, String identityId,
			String conversationId, ChatMessageCursorQuery query) {
		if (participantMapper.selectActiveParticipant(tenantId, conversationId, identityType.name(), identityId) == null) {
			throw new ArynBusinessException("会话不存在或无权访问");
		}
		Long beforeSeq = query == null ? null : query.getBeforeSeq();
		Long afterSeq = query == null ? null : query.getAfterSeq();
		if (beforeSeq != null && afterSeq != null) {
			throw new ArynBusinessException("beforeSeq 和 afterSeq 不能同时使用");
		}
		int limit = normalizeLimit(query == null ? null : query.getLimit());
		List<MessageChat> rows = chatMapper.selectCursorPage(tenantId, conversationId, beforeSeq, afterSeq, limit + 1);
		boolean hasMore = rows.size() > limit;
		if (hasMore) {
			rows = new ArrayList<>(rows.subList(0, limit));
		}
		ChatMessagePageVO result = new ChatMessagePageVO();
		result.setRecords(rows.stream().map(this::toVO).toList());
		result.setHasMore(hasMore);
		if (hasMore && !rows.isEmpty()) {
			if (afterSeq != null) {
				result.setNextAfterSeq(rows.get(rows.size() - 1).getSeqNo());
			}
			else {
				result.setNextBeforeSeq(rows.get(rows.size() - 1).getSeqNo());
			}
		}
		return result;
	}

	private MessageConversation requireWritableConversation(String tenantId, String conversationId,
			MessageIdentityType senderType, String senderId) {
		MessageConversation conversation = conversationMapper.selectByIdForUpdate(tenantId, conversationId);
		if (conversation == null) {
			throw new ArynBusinessException("会话不存在");
		}
		if (ConversationStatus.CLOSED.name().equals(conversation.getStatus())) {
			throw new ArynBusinessException("已关闭会话不能发送消息");
		}
		MessageParticipant participant = participantMapper.selectActiveParticipant(tenantId, conversationId,
				senderType.name(), senderId);
		if (participant == null) {
			throw new ArynBusinessException("无权在该会话发送消息");
		}
		return conversation;
	}

	private ChatMessageVO append(MessageConversation conversation, MessageIdentityType senderType, String senderId,
			String senderName, String senderAvatar, ChatMessageType messageType, String content, String payload,
			String clientMessageId, String quotedMessageId) {
		long nextSeq = (conversation.getLastSeq() == null ? 0L : conversation.getLastSeq()) + 1;
		LocalDateTime now = LocalDateTime.now();
		MessageChat chat = new MessageChat();
		chat.setId(IdWorker.getIdStr());
		chat.setTenantId(conversation.getTenantId());
		chat.setConversationId(conversation.getId());
		chat.setSeqNo(nextSeq);
		chat.setSenderType(senderType.name());
		chat.setSenderId(senderId);
		chat.setSenderName(senderName);
		chat.setSenderAvatar(senderAvatar);
		chat.setMessageType(messageType.name());
		chat.setContent(content);
		chat.setPayload(payload);
		chat.setClientMessageId(clientMessageId);
		chat.setQuotedMessageId(quotedMessageId);
		chat.setRecallStatus(CommonConstants.NO);
		chat.setCreateBy(senderId);
		chat.setCreateTime(now);
		chat.setDelFlag(CommonConstants.NO);
		chatMapper.insert(chat);

		conversation.setLastSeq(nextSeq);
		conversation.setLastMessageId(chat.getId());
		conversation.setLastMessageSummary(summary(messageType, content));
		conversation.setLastMessageTime(now);
		if (senderType == MessageIdentityType.SYS_USER
				&& ConversationStatus.ASSIGNED.name().equals(conversation.getStatus())) {
			conversation.setStatus(ConversationStatus.ACTIVE.name());
		}
		conversation.setUpdateBy(senderId);
		conversation.setUpdateTime(now);
		conversationMapper.updateById(conversation);
		runAfterCommit(() -> pushService.pushConversationMessage(conversation.getTenantId(), conversation.getId(),
				chat.getId(), chat.getSeqNo()));
		return toVO(chat);
	}

	private void validateClientMessage(ChatMessageSendRequest request) {
		if (request == null || request.getMessageType() == null || !CLIENT_TYPES.contains(request.getMessageType())) {
			throw new ArynBusinessException("不支持的消息类型");
		}
		if (!StringUtils.hasText(request.getClientMessageId())) {
			throw new ArynBusinessException("clientMessageId 不能为空");
		}
		if (request.getMessageType() == ChatMessageType.TEXT) {
			if (!StringUtils.hasText(request.getContent())) {
				throw new ArynBusinessException("文本消息不能为空");
			}
			if (StringUtils.hasText(request.getPayload())) {
				throw new ArynBusinessException("文本消息不能携带结构化负载");
			}
			return;
		}
		validatePayload(request.getPayload(), request.getMessageType());
	}

	private void validatePayload(String payload, ChatMessageType messageType) {
		if (!StringUtils.hasText(payload)) {
			throw new ArynBusinessException("结构化消息负载不能为空");
		}
		try {
			JsonNode node = objectMapper.readTree(payload);
			if (!node.isObject()) {
				throw new ArynBusinessException("结构化消息负载必须是 JSON 对象");
			}
			switch (messageType) {
				case IMAGE -> {
					requireText(node, "fileId");
					requireText(node, "url");
					String mimeType = requireText(node, "mimeType");
					if (!mimeType.startsWith("image/")) {
						throw new ArynBusinessException("图片 MIME 类型不合法");
					}
				}
				case PRODUCT_CARD -> requireText(node, "productId");
				case ORDER_CARD -> requireText(node, "orderId");
				case REFUND_CARD -> requireText(node, "refundId");
				case NOTICE_CARD -> requireText(node, "noticeId");
				case SYSTEM -> requireText(node, "code");
				default -> throw new ArynBusinessException("不支持的结构化消息类型");
			}
		}
		catch (JsonProcessingException exception) {
			throw new ArynBusinessException("结构化消息负载不是合法 JSON");
		}
	}

	private String requireText(JsonNode node, String field) {
		JsonNode value = node.get(field);
		if (value == null || !value.isTextual() || !StringUtils.hasText(value.asText())) {
			throw new ArynBusinessException("结构化消息缺少字段: " + field);
		}
		return value.asText();
	}

	private void assertSameConversation(MessageChat existing, String conversationId) {
		if (!conversationId.equals(existing.getConversationId())) {
			throw new ArynBusinessException("clientMessageId 已用于其他会话");
		}
	}

	private String summary(ChatMessageType type, String content) {
		if (type == ChatMessageType.TEXT) {
			return content.length() <= 200 ? content : content.substring(0, 200);
		}
		return switch (type) {
			case IMAGE -> "[图片]";
			case PRODUCT_CARD -> "[商品]";
			case ORDER_CARD -> "[订单]";
			case REFUND_CARD -> "[退款]";
			case NOTICE_CARD -> "[通知]";
			case SYSTEM -> "[系统消息]";
			default -> "[消息]";
		};
	}

	private ChatMessageVO toVO(MessageChat chat) {
		ChatMessageVO vo = new ChatMessageVO();
		BeanUtils.copyProperties(chat, vo);
		return vo;
	}

	private int normalizeLimit(Integer requested) {
		if (requested == null || requested <= 0) {
			return DEFAULT_LIMIT;
		}
		return Math.min(requested, MAX_LIMIT);
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
