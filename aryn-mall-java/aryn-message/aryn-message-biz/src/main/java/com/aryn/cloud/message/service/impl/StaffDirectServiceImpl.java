package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.entity.MessageConversation;
import com.aryn.cloud.message.api.entity.MessageParticipant;
import com.aryn.cloud.message.api.enums.ConversationStatus;
import com.aryn.cloud.message.api.enums.ConversationType;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;
import com.aryn.cloud.message.mapper.MessageConversationMapper;
import com.aryn.cloud.message.mapper.MessageParticipantMapper;
import com.aryn.cloud.message.service.StaffDirectService;
import com.aryn.cloud.upms.api.dto.StaffMessageAudienceRequest;
import com.aryn.cloud.upms.api.remote.RemoteMessageStaffService;
import com.aryn.cloud.upms.api.vo.StaffMessageAudiencePageVO;
import com.aryn.cloud.upms.api.vo.StaffMessageRecipientVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 工作人员组合唯一键驱动的一对一私信实现。 */
@Service
@RequiredArgsConstructor
public class StaffDirectServiceImpl implements StaffDirectService {

	private final MessageConversationMapper conversationMapper;
	private final MessageParticipantMapper participantMapper;

	@DubboReference
	private final RemoteMessageStaffService staffService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ConversationVO getOrCreate(String tenantId, String currentStaffId, String currentStaffName,
			String currentStaffAvatar, String targetStaffId) {
		if (currentStaffId.equals(targetStaffId)) {
			throw new ArynBusinessException("不能创建与自己的私信会话");
		}
		StaffMessageRecipientVO target = requireTargetStaff(tenantId, targetStaffId);
		String pairKey = pairKey(currentStaffId, targetStaffId);
		MessageConversation existing = conversationMapper.selectActiveStaffConversation(tenantId, pairKey);
		if (existing != null) {
			return requireView(tenantId, currentStaffId, existing.getId());
		}
		LocalDateTime now = LocalDateTime.now();
		MessageConversation conversation = new MessageConversation();
		conversation.setId(IdWorker.getIdStr());
		conversation.setTenantId(tenantId);
		conversation.setConversationType(ConversationType.STAFF_DIRECT.name());
		conversation.setQueueCode("DIRECT");
		conversation.setStaffPairKey(pairKey);
		conversation.setStatus(ConversationStatus.ACTIVE.name());
		conversation.setLastSeq(0L);
		conversation.setCreateBy(currentStaffId);
		conversation.setCreateTime(now);
		conversation.setDelFlag(CommonConstants.NO);
		try {
			conversationMapper.insert(conversation);
		}
		catch (DuplicateKeyException exception) {
			MessageConversation concurrent = conversationMapper.selectActiveStaffConversation(tenantId, pairKey);
			if (concurrent == null) {
				throw exception;
			}
			return requireView(tenantId, currentStaffId, concurrent.getId());
		}
		participantMapper.insertIgnore(participant(conversation, currentStaffId, currentStaffName, currentStaffAvatar));
		participantMapper.insertIgnore(participant(conversation, targetStaffId, target.getNickname(), target.getAvatar()));
		return requireView(tenantId, currentStaffId, conversation.getId());
	}

	private StaffMessageRecipientVO requireTargetStaff(String tenantId, String targetStaffId) {
		StaffMessageAudienceRequest request = new StaffMessageAudienceRequest();
		request.setTenantId(tenantId);
		request.setUserIds(List.of(targetStaffId));
		request.setLimit(1);
		StaffMessageAudiencePageVO page = staffService.queryRecipients(request);
		if (page == null || page.getRecords() == null || page.getRecords().size() != 1
				|| !targetStaffId.equals(page.getRecords().get(0).getId())) {
			throw new ArynBusinessException("目标工作人员不存在、已禁用或不属于当前租户");
		}
		return page.getRecords().get(0);
	}

	private MessageParticipant participant(MessageConversation conversation, String staffId, String name,
			String avatar) {
		LocalDateTime now = LocalDateTime.now();
		MessageParticipant participant = new MessageParticipant();
		participant.setId(IdWorker.getIdStr());
		participant.setTenantId(conversation.getTenantId());
		participant.setConversationId(conversation.getId());
		participant.setParticipantType(MessageIdentityType.SYS_USER.name());
		participant.setParticipantId(staffId);
		participant.setParticipantName(name);
		participant.setParticipantAvatar(avatar);
		participant.setLastReadSeq(0L);
		participant.setJoinedTime(now);
		participant.setParticipantStatus("ACTIVE");
		participant.setNotificationEnabled(CommonConstants.YES);
		participant.setCreateBy(staffId);
		participant.setCreateTime(now);
		participant.setDelFlag(CommonConstants.NO);
		return participant;
	}

	private ConversationVO requireView(String tenantId, String staffId, String conversationId) {
		ConversationVO view = conversationMapper.selectForParticipant(tenantId, MessageIdentityType.SYS_USER.name(),
				staffId, conversationId);
		if (view == null) {
			throw new ArynBusinessException("私信会话创建失败，请重试");
		}
		return view;
	}

	private String pairKey(String firstStaffId, String secondStaffId) {
		return firstStaffId.compareTo(secondStaffId) < 0 ? firstStaffId + ":" + secondStaffId
				: secondStaffId + ":" + firstStaffId;
	}

}
