package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.message.api.dto.notice.NoticeAudienceSnapshot;
import com.aryn.cloud.message.api.entity.MessageDispatchTask;
import com.aryn.cloud.message.api.entity.MessageRecipient;
import com.aryn.cloud.message.api.enums.DispatchTaskStatus;
import com.aryn.cloud.message.api.enums.InboxStatus;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.mapper.MessageDispatchTaskMapper;
import com.aryn.cloud.message.mapper.MessageRecipientMapper;
import com.aryn.cloud.message.service.NoticeDispatchService;
import com.aryn.cloud.upms.api.dto.StaffMessageAudienceRequest;
import com.aryn.cloud.upms.api.remote.RemoteMessageStaffService;
import com.aryn.cloud.upms.api.vo.StaffMessageAudiencePageVO;
import com.aryn.cloud.upms.api.vo.StaffMessageRecipientVO;
import com.aryn.cloud.user.api.dto.UserMessageAudienceRequest;
import com.aryn.cloud.user.api.remote.RemoteMessageAudienceService;
import com.aryn.cloud.user.api.vo.UserMessageAudiencePageVO;
import com.aryn.cloud.user.api.vo.UserMessageRecipientVO;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 可恢复、幂等的通知分发实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NoticeDispatchServiceImpl implements NoticeDispatchService {

	private static final int PAGE_SIZE = 1000;
	private static final int MAX_RETRY_COUNT = 3;

	private final MessageDispatchTaskMapper taskMapper;
	private final MessageRecipientMapper recipientMapper;
	private final ObjectMapper objectMapper;
	private final TransactionTemplate transactionTemplate;

	@DubboReference
	private final RemoteMessageAudienceService audienceService;

	@DubboReference
	private final RemoteMessageStaffService staffService;

	@Override
	@Async("hxAsyncExecutor")
	public void dispatch(String tenantId, String taskId) {
		ArynTenantContextHolder.removeTenantId();
		ArynTenantContextHolder.setTenantId(tenantId);
		MessageDispatchTask task = null;
		try {
			task = claimTask(tenantId, taskId);
			if (task == null) {
				return;
			}
			NoticeAudienceSnapshot audience = readAudience(task.getAudienceCondition());
			if (MessageIdentityType.MALL_USER.name().equals(task.getTargetType())) {
				dispatchMallUsers(task, audience);
			}
			else if (MessageIdentityType.SYS_USER.name().equals(task.getTargetType())) {
				dispatchStaff(task, audience);
			}
			else {
				throw new IllegalArgumentException("不支持的通知目标端: " + task.getTargetType());
			}
			finishSuccess(task);
		}
		catch (Exception exception) {
			log.error("通知分发失败 tenantId={}, taskId={}", tenantId, taskId, exception);
			finishFailure(tenantId, taskId, task, exception);
		}
		finally {
			ArynTenantContextHolder.removeTenantId();
		}
	}

	private MessageDispatchTask claimTask(String tenantId, String taskId) {
		return transactionTemplate.execute(status -> {
			MessageDispatchTask task = taskMapper.selectByIdForUpdate(tenantId, taskId);
			if (task == null || DispatchTaskStatus.SUCCEEDED.name().equals(task.getStatus())
					|| DispatchTaskStatus.PARTIAL_FAILED.name().equals(task.getStatus())
					|| DispatchTaskStatus.FAILED.name().equals(task.getStatus())) {
				return null;
			}
			task.setStatus(DispatchTaskStatus.RUNNING.name());
			task.setRetryCount((task.getRetryCount() == null ? 0 : task.getRetryCount()) + 1);
			task.setLastHeartbeatTime(LocalDateTime.now());
			task.setErrorSummary(null);
			taskMapper.updateById(task);
			return task;
		});
	}

	private void dispatchMallUsers(MessageDispatchTask task, NoticeAudienceSnapshot audience) {
		String cursor = task.getCursorValue();
		boolean hasMore;
		do {
			UserMessageAudienceRequest request = new UserMessageAudienceRequest();
			request.setTenantId(task.getTenantId());
			request.setCursor(cursor);
			request.setLimit(PAGE_SIZE);
			request.setUserIds(audience.getMallUserIds());
			request.setMemberLevelId(audience.getMemberLevelId());
			request.setMemberTagId(audience.getMemberTagId());
			UserMessageAudiencePageVO page = audienceService.queryRecipients(request);
			List<UserMessageRecipientVO> records = page == null ? List.of() : page.getRecords();
			int inserted = insertMallRecipients(task, records);
			cursor = page == null ? null : page.getNextCursor();
			hasMore = page != null && page.isHasMore();
			updateProgress(task, cursor, inserted);
			if (hasMore && !org.springframework.util.StringUtils.hasText(cursor)) {
				throw new IllegalStateException("会员受众分页缺少下一游标");
			}
		} while (hasMore);
	}

	private void dispatchStaff(MessageDispatchTask task, NoticeAudienceSnapshot audience) {
		String cursor = task.getCursorValue();
		boolean hasMore;
		do {
			StaffMessageAudienceRequest request = new StaffMessageAudienceRequest();
			request.setTenantId(task.getTenantId());
			request.setCursor(cursor);
			request.setLimit(PAGE_SIZE);
			request.setUserIds(audience.getStaffUserIds());
			request.setRoleIds(audience.getRoleIds());
			request.setDeptIds(audience.getDeptIds());
			StaffMessageAudiencePageVO page = staffService.queryRecipients(request);
			List<StaffMessageRecipientVO> records = page == null ? List.of() : page.getRecords();
			int inserted = insertStaffRecipients(task, records);
			cursor = page == null ? null : page.getNextCursor();
			hasMore = page != null && page.isHasMore();
			updateProgress(task, cursor, inserted);
			if (hasMore && !org.springframework.util.StringUtils.hasText(cursor)) {
				throw new IllegalStateException("工作人员受众分页缺少下一游标");
			}
		} while (hasMore);
	}

	private int insertMallRecipients(MessageDispatchTask task, List<UserMessageRecipientVO> records) {
		if (CollectionUtils.isEmpty(records)) {
			return 0;
		}
		List<MessageRecipient> recipients = new ArrayList<>(records.size());
		for (UserMessageRecipientVO record : records) {
			recipients.add(recipient(task, MessageIdentityType.MALL_USER, record.getId(), record.getNickname()));
		}
		return recipientMapper.insertIgnoreBatch(recipients);
	}

	private int insertStaffRecipients(MessageDispatchTask task, List<StaffMessageRecipientVO> records) {
		if (CollectionUtils.isEmpty(records)) {
			return 0;
		}
		List<MessageRecipient> recipients = new ArrayList<>(records.size());
		for (StaffMessageRecipientVO record : records) {
			recipients.add(recipient(task, MessageIdentityType.SYS_USER, record.getId(), record.getNickname()));
		}
		return recipientMapper.insertIgnoreBatch(recipients);
	}

	private MessageRecipient recipient(MessageDispatchTask task, MessageIdentityType type, String id, String name) {
		LocalDateTime now = LocalDateTime.now();
		MessageRecipient recipient = new MessageRecipient();
		recipient.setId(IdWorker.getIdStr());
		recipient.setTenantId(task.getTenantId());
		recipient.setMessageId(task.getMessageId());
		recipient.setRecipientType(type.name());
		recipient.setRecipientId(id);
		recipient.setRecipientName(name);
		recipient.setReadStatus(CommonConstants.NO);
		recipient.setReceivedTime(now);
		recipient.setInboxStatus(InboxStatus.VISIBLE.name());
		recipient.setCreateBy("NOTICE_DISPATCH");
		recipient.setCreateTime(now);
		recipient.setDelFlag(CommonConstants.NO);
		return recipient;
	}

	private void updateProgress(MessageDispatchTask task, String cursor, int inserted) {
		task.setCursorValue(cursor);
		task.setSuccessCount((task.getSuccessCount() == null ? 0L : task.getSuccessCount()) + inserted);
		task.setEstimatedCount(task.getSuccessCount());
		task.setLastHeartbeatTime(LocalDateTime.now());
		taskMapper.updateById(task);
	}

	private void finishSuccess(MessageDispatchTask task) {
		task.setStatus(DispatchTaskStatus.SUCCEEDED.name());
		task.setLastHeartbeatTime(LocalDateTime.now());
		task.setErrorSummary(null);
		taskMapper.updateById(task);
	}

	private void finishFailure(String tenantId, String taskId, MessageDispatchTask task, Exception exception) {
		MessageDispatchTask target = task != null ? task : taskMapper.selectByIdForUpdate(tenantId, taskId);
		if (target == null) {
			return;
		}
		int retryCount = target.getRetryCount() == null ? 0 : target.getRetryCount();
		if (retryCount < MAX_RETRY_COUNT) {
			target.setStatus(DispatchTaskStatus.PENDING.name());
		}
		else if (target.getSuccessCount() != null && target.getSuccessCount() > 0) {
			target.setStatus(DispatchTaskStatus.PARTIAL_FAILED.name());
		}
		else {
			target.setStatus(DispatchTaskStatus.FAILED.name());
		}
		target.setFailureCount((target.getFailureCount() == null ? 0L : target.getFailureCount()) + 1);
		target.setLastHeartbeatTime(LocalDateTime.now());
		target.setErrorSummary(abbreviate(exception.getMessage(), 1000));
		taskMapper.updateById(target);
	}

	private NoticeAudienceSnapshot readAudience(String json) {
		if (!org.springframework.util.StringUtils.hasText(json)) {
			return new NoticeAudienceSnapshot();
		}
		try {
			return objectMapper.readValue(json, NoticeAudienceSnapshot.class);
		}
		catch (JsonProcessingException exception) {
			throw new IllegalArgumentException("通知受众快照格式错误", exception);
		}
	}

	private String abbreviate(String message, int maxLength) {
		if (message == null) {
			return "未知分发错误";
		}
		return message.length() <= maxLength ? message : message.substring(0, maxLength);
	}

}
