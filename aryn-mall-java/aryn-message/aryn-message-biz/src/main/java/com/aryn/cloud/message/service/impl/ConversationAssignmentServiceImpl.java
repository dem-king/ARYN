package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.entity.MessageAgent;
import com.aryn.cloud.message.api.entity.MessageAssignmentLog;
import com.aryn.cloud.message.api.entity.MessageConversation;
import com.aryn.cloud.message.api.entity.MessageParticipant;
import com.aryn.cloud.message.api.enums.AssignmentActionType;
import com.aryn.cloud.message.api.enums.ConversationStatus;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;
import com.aryn.cloud.message.mapper.MessageAgentMapper;
import com.aryn.cloud.message.mapper.MessageAssignmentLogMapper;
import com.aryn.cloud.message.mapper.MessageConversationMapper;
import com.aryn.cloud.message.mapper.MessageParticipantMapper;
import com.aryn.cloud.message.service.AgentService;
import com.aryn.cloud.message.service.ChatMessageService;
import com.aryn.cloud.message.service.ConversationAssignmentService;
import com.aryn.cloud.upms.api.remote.RemoteMessageStaffService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/** 基于数据库条件更新的客服会话分配状态机。 */
@Service
@RequiredArgsConstructor
public class ConversationAssignmentServiceImpl implements ConversationAssignmentService {

	private static final int CANDIDATE_LIMIT = 200;

	private final MessageConversationMapper conversationMapper;
	private final MessageParticipantMapper participantMapper;
	private final MessageAgentMapper agentMapper;
	private final MessageAssignmentLogMapper assignmentLogMapper;
	private final AgentService agentService;
	private final ChatMessageService chatMessageService;
	private final TransactionTemplate transactionTemplate;

	@DubboReference
	private final RemoteMessageStaffService staffService;

	@Override
	public String autoAssign(String tenantId, String conversationId) {
		List<MessageAgent> candidates = agentMapper.selectAssignmentCandidates(tenantId, CANDIDATE_LIMIT);
		for (MessageAgent candidate : candidates) {
			if (!agentService.isAutoAssignable(tenantId, candidate.getStaffId())
					|| !staffService.isCustomerServiceStaff(tenantId, candidate.getStaffId())) {
				continue;
			}
			try {
				Boolean assigned = transactionTemplate.execute(status -> tryAssign(tenantId, conversationId,
						candidate.getStaffId(), AssignmentActionType.AUTO_ASSIGN, "自动分配"));
				if (Boolean.TRUE.equals(assigned)) {
					return candidate.getStaffId();
				}
			}
			catch (AssignmentConflictException exception) {
				return null;
			}
		}
		return null;
	}

	@Override
	public List<ConversationVO> waiting(String tenantId, String queueCode, int limit) {
		String normalizedQueue = StringUtils.hasText(queueCode) ? queueCode : "DEFAULT";
		int normalizedLimit = Math.min(Math.max(limit, 1), 100);
		return conversationMapper.selectWaiting(tenantId, normalizedQueue, normalizedLimit).stream().map(conversation -> {
			ConversationVO vo = new ConversationVO();
			BeanUtils.copyProperties(conversation, vo);
			vo.setUnreadCount(0L);
			vo.setLastReadSeq(0L);
			return vo;
		}).toList();
	}

	@Override
	public void claim(String tenantId, String conversationId, String staffId, String reason) {
		requireQualifiedStaff(tenantId, staffId);
		Boolean claimed;
		try {
			claimed = transactionTemplate.execute(status -> tryAssign(tenantId, conversationId, staffId,
					AssignmentActionType.CLAIM, StringUtils.hasText(reason) ? reason : "手动领取"));
		}
		catch (AssignmentConflictException exception) {
			claimed = false;
		}
		if (!Boolean.TRUE.equals(claimed)) {
			throw new ArynBusinessException("会话已被领取或客服已达接待上限");
		}
	}

	@Override
	public void transfer(String tenantId, String conversationId, String operatorId, String targetStaffId, String reason,
			boolean supervisor) {
		requireQualifiedStaff(tenantId, targetStaffId);
		transactionTemplate.executeWithoutResult(status -> {
			MessageConversation conversation = requireLockedConversation(tenantId, conversationId);
			if (ConversationStatus.CLOSED.name().equals(conversation.getStatus())) {
				throw new ArynBusinessException("已关闭会话不能转交");
			}
			String originalStaffId = conversation.getAssignedStaffId();
			if (!supervisor && !operatorId.equals(originalStaffId)) {
				throw new ArynBusinessException("只有当前客服或主管可以转交会话");
			}
			if (targetStaffId.equals(originalStaffId)) {
				return;
			}
			if (agentMapper.incrementActiveCount(tenantId, targetStaffId) != 1) {
				throw new ArynBusinessException("目标客服已达接待上限");
			}
			if (StringUtils.hasText(originalStaffId)) {
				agentMapper.decrementActiveCount(tenantId, originalStaffId);
				participantMapper.deactivate(tenantId, conversationId, MessageIdentityType.SYS_USER.name(), originalStaffId);
			}
			conversation.setAssignedStaffId(targetStaffId);
			conversation.setStatus(ConversationStatus.ASSIGNED.name());
			conversation.setUpdateBy(operatorId);
			conversation.setUpdateTime(LocalDateTime.now());
			conversationMapper.updateById(conversation);
			participantMapper.upsertActive(staffParticipant(conversation, targetStaffId));
			writeLog(conversation, AssignmentActionType.TRANSFER, originalStaffId, targetStaffId,
					MessageIdentityType.SYS_USER, operatorId, reason);
		});
	}

	@Override
	public void close(String tenantId, String conversationId, MessageIdentityType operatorType, String operatorId,
			String reason, boolean supervisor) {
		transactionTemplate.executeWithoutResult(status -> {
			MessageConversation conversation = requireLockedConversation(tenantId, conversationId);
			if (ConversationStatus.CLOSED.name().equals(conversation.getStatus())) {
				return;
			}
			boolean memberOwns = operatorType == MessageIdentityType.MALL_USER
					&& operatorId.equals(conversation.getCustomerId());
			boolean staffOwns = operatorType == MessageIdentityType.SYS_USER
					&& operatorId.equals(conversation.getAssignedStaffId());
			if (!supervisor && !memberOwns && !staffOwns) {
				throw new ArynBusinessException("无权关闭该会话");
			}
			String assignedStaffId = conversation.getAssignedStaffId();
			if (StringUtils.hasText(assignedStaffId)) {
				agentMapper.decrementActiveCount(tenantId, assignedStaffId);
			}
			LocalDateTime now = LocalDateTime.now();
			conversation.setStatus(ConversationStatus.CLOSED.name());
			conversation.setClosedTime(now);
			conversation.setReopenDeadline(now.plusHours(24));
			conversation.setCloseReason(reason);
			conversation.setUpdateBy(operatorId);
			conversation.setUpdateTime(now);
			conversationMapper.updateById(conversation);
			writeLog(conversation, AssignmentActionType.CLOSE, assignedStaffId, null, operatorType, operatorId, reason);
			chatMessageService.sendSystem(tenantId, conversationId, "会话已关闭",
					"{\"code\":\"CONVERSATION_CLOSED\"}", "close-" + conversationId + "-" + now.toInstant(java.time.ZoneOffset.UTC).toEpochMilli());
		});
	}

	@Override
	public String reopenCustomerConversation(String tenantId, String customerId, String queueCode) {
		MessageConversation recent = conversationMapper.selectRecentlyClosedCustomerConversation(tenantId, customerId,
				queueCode, LocalDateTime.now());
		if (recent == null) {
			return null;
		}
		String originalStaffId = recent.getAssignedStaffId();
		Boolean reopened = transactionTemplate.execute(status -> {
			MessageConversation conversation = requireLockedConversation(tenantId, recent.getId());
			if (!ConversationStatus.CLOSED.name().equals(conversation.getStatus())
					|| conversation.getReopenDeadline() == null
					|| conversation.getReopenDeadline().isBefore(LocalDateTime.now())) {
				return false;
			}
			conversation.setStatus(ConversationStatus.WAITING.name());
			conversation.setAssignedStaffId(null);
			conversation.setClosedTime(null);
			conversation.setReopenDeadline(null);
			conversation.setCloseReason(null);
			conversation.setUpdateBy(customerId);
			conversation.setUpdateTime(LocalDateTime.now());
			conversationMapper.updateById(conversation);
			if (StringUtils.hasText(originalStaffId)) {
				participantMapper.deactivate(tenantId, conversation.getId(), MessageIdentityType.SYS_USER.name(),
						originalStaffId);
			}
			writeLog(conversation, AssignmentActionType.REOPEN, originalStaffId, null,
					MessageIdentityType.MALL_USER, customerId, "24 小时内重开");
			return true;
		});
		if (!Boolean.TRUE.equals(reopened)) {
			return null;
		}
		if (StringUtils.hasText(originalStaffId) && agentService.isAutoAssignable(tenantId, originalStaffId)
				&& staffService.isCustomerServiceStaff(tenantId, originalStaffId)) {
			try {
				Boolean assigned = transactionTemplate.execute(status -> tryAssign(tenantId, recent.getId(), originalStaffId,
						AssignmentActionType.REOPEN, "重开优先原客服"));
				if (Boolean.TRUE.equals(assigned)) {
					return recent.getId();
				}
			}
			catch (AssignmentConflictException ignored) {
				return recent.getId();
			}
		}
		autoAssign(tenantId, recent.getId());
		return recent.getId();
	}

	private boolean tryAssign(String tenantId, String conversationId, String staffId, AssignmentActionType action,
			String reason) {
		if (agentMapper.incrementActiveCount(tenantId, staffId) != 1) {
			return false;
		}
		if (conversationMapper.assignWaiting(tenantId, conversationId, staffId) != 1) {
			throw new AssignmentConflictException();
		}
		MessageConversation conversation = conversationMapper.selectByIdForUpdate(tenantId, conversationId);
		participantMapper.upsertActive(staffParticipant(conversation, staffId));
		writeLog(conversation, action, null, staffId, MessageIdentityType.SYSTEM, "SYSTEM", reason);
		return true;
	}

	private void requireQualifiedStaff(String tenantId, String staffId) {
		if (!staffService.isCustomerServiceStaff(tenantId, staffId)) {
			throw new ArynBusinessException("工作人员不具备客服资格");
		}
		MessageAgent agent = agentMapper.selectByStaffId(tenantId, staffId);
		if (agent == null || !CommonConstants.YES.equals(agent.getEnabled())) {
			throw new ArynBusinessException("客服坐席未启用");
		}
	}

	private MessageConversation requireLockedConversation(String tenantId, String conversationId) {
		MessageConversation conversation = conversationMapper.selectByIdForUpdate(tenantId, conversationId);
		if (conversation == null) {
			throw new ArynBusinessException("会话不存在");
		}
		return conversation;
	}

	private MessageParticipant staffParticipant(MessageConversation conversation, String staffId) {
		LocalDateTime now = LocalDateTime.now();
		MessageParticipant participant = new MessageParticipant();
		participant.setId(IdWorker.getIdStr());
		participant.setTenantId(conversation.getTenantId());
		participant.setConversationId(conversation.getId());
		participant.setParticipantType(MessageIdentityType.SYS_USER.name());
		participant.setParticipantId(staffId);
		participant.setLastReadSeq(conversation.getLastSeq() == null ? 0L : conversation.getLastSeq());
		participant.setJoinedTime(now);
		participant.setParticipantStatus("ACTIVE");
		participant.setNotificationEnabled(CommonConstants.YES);
		participant.setCreateBy(staffId);
		participant.setCreateTime(now);
		participant.setDelFlag(CommonConstants.NO);
		return participant;
	}

	private void writeLog(MessageConversation conversation, AssignmentActionType action, String fromStaffId,
			String toStaffId, MessageIdentityType operatorType, String operatorId, String reason) {
		MessageAssignmentLog log = new MessageAssignmentLog();
		log.setId(IdWorker.getIdStr());
		log.setTenantId(conversation.getTenantId());
		log.setConversationId(conversation.getId());
		log.setActionType(action.name());
		log.setFromStaffId(fromStaffId);
		log.setToStaffId(toStaffId);
		log.setOperatorType(operatorType.name());
		log.setOperatorId(operatorId);
		log.setReason(reason);
		log.setCreateBy(operatorId);
		log.setCreateTime(LocalDateTime.now());
		log.setDelFlag(CommonConstants.NO);
		assignmentLogMapper.insert(log);
	}

	private static class AssignmentConflictException extends ArynBusinessException {
		AssignmentConflictException() {
			super("会话已被其他客服领取");
		}
	}

}
