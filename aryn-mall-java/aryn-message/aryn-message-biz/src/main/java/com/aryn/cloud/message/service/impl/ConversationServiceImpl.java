package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.dto.conversation.ConversationInboxQuery;
import com.aryn.cloud.message.api.entity.MessageConversation;
import com.aryn.cloud.message.api.entity.MessageParticipant;
import com.aryn.cloud.message.api.enums.ConversationStatus;
import com.aryn.cloud.message.api.enums.ConversationType;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ConversationInboxPageVO;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;
import com.aryn.cloud.message.mapper.MessageConversationMapper;
import com.aryn.cloud.message.mapper.MessageParticipantMapper;
import com.aryn.cloud.message.service.ConversationService;
import com.aryn.cloud.message.service.ConversationAssignmentService;
import com.aryn.cloud.user.api.dto.UserMessageAudienceRequest;
import com.aryn.cloud.user.api.remote.RemoteMessageAudienceService;
import com.aryn.cloud.user.api.vo.UserMessageAudiencePageVO;
import com.aryn.cloud.user.api.vo.UserMessageRecipientVO;
import org.apache.dubbo.config.annotation.DubboReference;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 会话创建、查询和已读实现。 */
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

	private static final String DEFAULT_QUEUE = "DEFAULT";
	private static final int DEFAULT_LIMIT = 20;
	private static final int MAX_LIMIT = 100;

	private final MessageConversationMapper conversationMapper;
	private final MessageParticipantMapper participantMapper;
	private final ObjectMapper objectMapper;
	private final ConversationAssignmentService assignmentService;

	@DubboReference
	private final RemoteMessageAudienceService audienceService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ConversationVO getOrCreateCustomerService(String tenantId, String customerId, String customerName,
			String customerAvatar, String queueCode, String contextPayload) {
		String normalizedQueue = StringUtils.hasText(queueCode) ? queueCode.trim() : DEFAULT_QUEUE;
		validateJson(contextPayload, "会话业务上下文格式错误");
		MessageConversation existing = conversationMapper.selectActiveCustomerConversation(tenantId, customerId,
				normalizedQueue);
		if (existing != null) {
			return requireParticipantView(tenantId, MessageIdentityType.MALL_USER, customerId, existing.getId());
		}
		String reopenedConversationId = assignmentService.reopenCustomerConversation(tenantId, customerId,
				normalizedQueue);
		if (reopenedConversationId != null) {
			return requireParticipantView(tenantId, MessageIdentityType.MALL_USER, customerId, reopenedConversationId);
		}
		MessageConversation conversation = new MessageConversation();
		conversation.setId(IdWorker.getIdStr());
		conversation.setTenantId(tenantId);
		conversation.setConversationType(ConversationType.CUSTOMER_SERVICE.name());
		conversation.setQueueCode(normalizedQueue);
		conversation.setCustomerId(customerId);
		conversation.setStatus(ConversationStatus.WAITING.name());
		conversation.setLastSeq(0L);
		conversation.setContextPayload(contextPayload);
		conversation.setCreateBy(customerId);
		conversation.setCreateTime(LocalDateTime.now());
		conversation.setDelFlag(CommonConstants.NO);
		try {
			conversationMapper.insert(conversation);
		}
		catch (DuplicateKeyException exception) {
			MessageConversation concurrent = conversationMapper.selectActiveCustomerConversation(tenantId, customerId,
					normalizedQueue);
			if (concurrent == null) {
				throw exception;
			}
			return requireParticipantView(tenantId, MessageIdentityType.MALL_USER, customerId, concurrent.getId());
		}
		participantMapper.insertIgnore(participant(conversation, MessageIdentityType.MALL_USER, customerId,
				customerName, customerAvatar));
		assignmentService.autoAssign(tenantId, conversation.getId());
		return requireParticipantView(tenantId, MessageIdentityType.MALL_USER, customerId, conversation.getId());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ConversationVO initiateCustomerService(String tenantId, String staffId, String customerId, String queueCode,
			String contextPayload) {
		String normalizedQueue = StringUtils.hasText(queueCode) ? queueCode.trim() : DEFAULT_QUEUE;
		validateJson(contextPayload, "会话业务上下文格式错误");
		UserMessageRecipientVO customer = requireCustomer(tenantId, customerId);
		MessageConversation conversation = conversationMapper.selectActiveCustomerConversation(tenantId, customerId,
				normalizedQueue);
		if (conversation != null) {
			if (StringUtils.hasText(conversation.getAssignedStaffId())
					&& !staffId.equals(conversation.getAssignedStaffId())) {
				throw new ArynBusinessException("该会员已有其他客服负责的活跃会话");
			}
			if (!StringUtils.hasText(conversation.getAssignedStaffId())) {
				assignmentService.claim(tenantId, conversation.getId(), staffId, "客服主动联系会员");
			}
			return requireParticipantView(tenantId, MessageIdentityType.SYS_USER, staffId, conversation.getId());
		}
		LocalDateTime now = LocalDateTime.now();
		conversation = new MessageConversation();
		conversation.setId(IdWorker.getIdStr());
		conversation.setTenantId(tenantId);
		conversation.setConversationType(ConversationType.CUSTOMER_SERVICE.name());
		conversation.setQueueCode(normalizedQueue);
		conversation.setCustomerId(customerId);
		conversation.setStatus(ConversationStatus.WAITING.name());
		conversation.setLastSeq(0L);
		conversation.setContextPayload(contextPayload);
		conversation.setCreateBy(staffId);
		conversation.setCreateTime(now);
		conversation.setDelFlag(CommonConstants.NO);
		try {
			conversationMapper.insert(conversation);
		}
		catch (DuplicateKeyException exception) {
			MessageConversation concurrent = conversationMapper.selectActiveCustomerConversation(tenantId, customerId,
					normalizedQueue);
			if (concurrent == null || (StringUtils.hasText(concurrent.getAssignedStaffId())
					&& !staffId.equals(concurrent.getAssignedStaffId()))) {
				throw new ArynBusinessException("该会员已有其他客服负责的活跃会话");
			}
			conversation = concurrent;
		}
		participantMapper.insertIgnore(participant(conversation, MessageIdentityType.MALL_USER, customerId,
				customer.getNickname(), customer.getAvatarUrl()));
		if (!StringUtils.hasText(conversation.getAssignedStaffId())) {
			assignmentService.claim(tenantId, conversation.getId(), staffId, "客服主动联系会员");
		}
		return requireParticipantView(tenantId, MessageIdentityType.SYS_USER, staffId, conversation.getId());
	}

	@Override
	public ConversationVO get(String tenantId, MessageIdentityType identityType, String identityId,
			String conversationId) {
		return requireParticipantView(tenantId, identityType, identityId, conversationId);
	}

	@Override
	public ConversationInboxPageVO inbox(String tenantId, MessageIdentityType identityType, String identityId,
			ConversationInboxQuery query) {
		int limit = normalizeLimit(query == null ? null : query.getLimit());
		String cursor = query == null ? null : query.getCursor();
		List<ConversationVO> records = conversationMapper.selectInbox(tenantId, identityType.name(), identityId,
				cursor, limit + 1);
		boolean hasMore = records.size() > limit;
		if (hasMore) {
			records = new ArrayList<>(records.subList(0, limit));
		}
		ConversationInboxPageVO result = new ConversationInboxPageVO();
		result.setRecords(records);
		result.setHasMore(hasMore);
		result.setNextCursor(hasMore && !records.isEmpty() ? records.get(records.size() - 1).getId() : null);
		return result;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void markRead(String tenantId, MessageIdentityType identityType, String identityId, String conversationId,
			long lastReadSeq) {
		MessageConversation conversation = conversationMapper.selectByIdForUpdate(tenantId, conversationId);
		if (conversation == null) {
			throw new ArynBusinessException("会话不存在");
		}
		MessageParticipant participant = participantMapper.selectActiveParticipant(tenantId, conversationId,
				identityType.name(), identityId);
		if (participant == null) {
			throw new ArynBusinessException("无权访问该会话");
		}
		long currentReadSeq = participant.getLastReadSeq() == null ? 0L : participant.getLastReadSeq();
		long lastSeq = conversation.getLastSeq() == null ? 0L : conversation.getLastSeq();
		if (lastReadSeq <= currentReadSeq) {
			return;
		}
		if (lastReadSeq > lastSeq) {
			throw new ArynBusinessException("已读游标不能超过会话最新消息");
		}
		if (participantMapper.updateLastReadSeq(tenantId, conversationId, identityType.name(), identityId,
				lastReadSeq) != 1) {
			throw new ArynBusinessException("已读状态更新失败，请重试");
		}
	}

	private ConversationVO requireParticipantView(String tenantId, MessageIdentityType identityType, String identityId,
			String conversationId) {
		ConversationVO view = conversationMapper.selectForParticipant(tenantId, identityType.name(), identityId,
				conversationId);
		if (view == null) {
			throw new ArynBusinessException("会话不存在或无权访问");
		}
		return view;
	}

	private MessageParticipant participant(MessageConversation conversation, MessageIdentityType type, String id,
			String name, String avatar) {
		LocalDateTime now = LocalDateTime.now();
		MessageParticipant participant = new MessageParticipant();
		participant.setId(IdWorker.getIdStr());
		participant.setTenantId(conversation.getTenantId());
		participant.setConversationId(conversation.getId());
		participant.setParticipantType(type.name());
		participant.setParticipantId(id);
		participant.setParticipantName(name);
		participant.setParticipantAvatar(avatar);
		participant.setLastReadSeq(0L);
		participant.setJoinedTime(now);
		participant.setParticipantStatus("ACTIVE");
		participant.setNotificationEnabled(CommonConstants.YES);
		participant.setCreateBy(id);
		participant.setCreateTime(now);
		participant.setDelFlag(CommonConstants.NO);
		return participant;
	}

	private UserMessageRecipientVO requireCustomer(String tenantId, String customerId) {
		UserMessageAudienceRequest request = new UserMessageAudienceRequest();
		request.setTenantId(tenantId);
		request.setUserIds(List.of(customerId));
		request.setLimit(1);
		UserMessageAudiencePageVO page = audienceService.queryRecipients(request);
		if (page == null || page.getRecords() == null || page.getRecords().size() != 1
				|| !customerId.equals(page.getRecords().get(0).getId())) {
			throw new ArynBusinessException("会员不存在、已禁用或不属于当前租户");
		}
		return page.getRecords().get(0);
	}

	private void validateJson(String json, String errorMessage) {
		if (!StringUtils.hasText(json)) {
			return;
		}
		try {
			objectMapper.readTree(json);
		}
		catch (JsonProcessingException exception) {
			throw new ArynBusinessException(errorMessage);
		}
	}

	private int normalizeLimit(Integer requested) {
		if (requested == null || requested <= 0) {
			return DEFAULT_LIMIT;
		}
		return Math.min(requested, MAX_LIMIT);
	}

}
