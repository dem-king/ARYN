package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.dto.notice.NoticeAudienceSnapshot;
import com.aryn.cloud.message.api.dto.notice.NoticeInboxQuery;
import com.aryn.cloud.message.api.dto.notice.NoticeSaveRequest;
import com.aryn.cloud.message.api.entity.MessageDispatchTask;
import com.aryn.cloud.message.api.entity.MessageNotice;
import com.aryn.cloud.message.api.entity.MessageRecipient;
import com.aryn.cloud.message.api.enums.DispatchTaskStatus;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.enums.NoticeDispatchStatus;
import com.aryn.cloud.message.api.enums.NoticeJumpType;
import com.aryn.cloud.message.api.enums.NoticeStatus;
import com.aryn.cloud.message.api.vo.notice.NoticeDispatchTaskVO;
import com.aryn.cloud.message.api.vo.notice.NoticeInboxItemVO;
import com.aryn.cloud.message.api.vo.notice.NoticeInboxPageVO;
import com.aryn.cloud.message.api.vo.notice.NoticeRecipientItemVO;
import com.aryn.cloud.message.api.vo.notice.NoticeRecipientStatsVO;
import com.aryn.cloud.message.api.vo.notice.NoticeVO;
import com.aryn.cloud.message.mapper.MessageDispatchTaskMapper;
import com.aryn.cloud.message.mapper.MessageNoticeMapper;
import com.aryn.cloud.message.mapper.MessageRecipientMapper;
import com.aryn.cloud.message.service.NoticeDispatchService;
import com.aryn.cloud.message.service.NoticeService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 通知管理和本人收件箱实现。 */
@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

	private static final int DEFAULT_INBOX_LIMIT = 20;
	private static final int MAX_INBOX_LIMIT = 100;

	private final MessageNoticeMapper noticeMapper;
	private final MessageDispatchTaskMapper dispatchTaskMapper;
	private final MessageRecipientMapper recipientMapper;
	private final NoticeDispatchService dispatchService;
	private final ObjectMapper objectMapper;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public NoticeVO createDraft(String tenantId, String senderId, String senderName, NoticeSaveRequest request) {
		validateRequest(request);
		MessageNotice notice = new MessageNotice();
		notice.setId(IdWorker.getIdStr());
		notice.setTenantId(tenantId);
		notice.setSenderType(MessageIdentityType.SYS_USER.name());
		notice.setSenderId(senderId);
		notice.setSenderName(senderName);
		notice.setStatus(NoticeStatus.DRAFT.name());
		notice.setCreateBy(senderId);
		notice.setCreateTime(LocalDateTime.now());
		notice.setDelFlag(CommonConstants.NO);
		applyRequest(notice, request);
		noticeMapper.insert(notice);
		return toVO(notice);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public NoticeVO updateDraft(String tenantId, String operatorId, String noticeId, NoticeSaveRequest request) {
		validateRequest(request);
		MessageNotice notice = requireLockedNotice(tenantId, noticeId);
		if (!NoticeStatus.DRAFT.name().equals(notice.getStatus())) {
			throw new ArynBusinessException("已发布或已撤回通知不可修改");
		}
		applyRequest(notice, request);
		notice.setUpdateBy(operatorId);
		notice.setUpdateTime(LocalDateTime.now());
		noticeMapper.updateById(notice);
		return toVO(notice);
	}

	@Override
	public NoticeVO get(String tenantId, String noticeId) {
		MessageNotice notice = noticeMapper.selectOne(Wrappers.<MessageNotice>lambdaQuery()
			.eq(MessageNotice::getTenantId, tenantId)
			.eq(MessageNotice::getId, noticeId));
		if (notice == null) {
			throw new ArynBusinessException("通知不存在");
		}
		NoticeVO vo = toVO(notice);
		enrichDelivery(List.of(vo), tenantId);
		return vo;
	}

	@Override
	public IPage<NoticeVO> page(String tenantId, String status, String keyword, Page<?> page) {
		String trimmedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
		Page<MessageNotice> sourcePage = new Page<>(page.getCurrent(), page.getSize());
		IPage<MessageNotice> source = noticeMapper.selectPage(sourcePage, Wrappers.<MessageNotice>lambdaQuery()
			.eq(MessageNotice::getTenantId, tenantId)
			.eq(StringUtils.hasText(status), MessageNotice::getStatus, status)
			.and(trimmedKeyword != null, wrapper -> wrapper
				.like(MessageNotice::getTitle, trimmedKeyword)
				.or().like(MessageNotice::getSummary, trimmedKeyword)
				.or().like(MessageNotice::getContent, trimmedKeyword))
			.orderByDesc(MessageNotice::getCreateTime));
		List<NoticeVO> records = source.getRecords().stream().map(this::toVO).toList();
		enrichDelivery(records, tenantId);
		Page<NoticeVO> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
		result.setRecords(records);
		return result;
	}

	@Override
	public IPage<NoticeRecipientItemVO> recipients(String tenantId, String noticeId, String readStatus,
			String recipientType, String keyword, Page<?> page) {
		MessageNotice notice = noticeMapper.selectOne(Wrappers.<MessageNotice>lambdaQuery()
			.eq(MessageNotice::getTenantId, tenantId)
			.eq(MessageNotice::getId, noticeId));
		if (notice == null) {
			throw new ArynBusinessException("通知不存在");
		}
		String trimmedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;
		Page<MessageRecipient> sourcePage = new Page<>(page.getCurrent(), page.getSize());
		IPage<MessageRecipient> source = recipientMapper.selectPage(sourcePage,
				Wrappers.<MessageRecipient>lambdaQuery()
					.eq(MessageRecipient::getTenantId, tenantId)
					.eq(MessageRecipient::getMessageId, noticeId)
					.eq(StringUtils.hasText(readStatus), MessageRecipient::getReadStatus, readStatus)
					.eq(StringUtils.hasText(recipientType), MessageRecipient::getRecipientType, recipientType)
					.and(trimmedKeyword != null, wrapper -> wrapper
						.like(MessageRecipient::getRecipientName, trimmedKeyword)
						.or().like(MessageRecipient::getRecipientId, trimmedKeyword))
					.orderByDesc(MessageRecipient::getReceivedTime)
					.orderByAsc(MessageRecipient::getId));
		Page<NoticeRecipientItemVO> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
		result.setRecords(source.getRecords().stream().map(this::toRecipientVO).toList());
		return result;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void publish(String tenantId, String operatorId, String noticeId) {
		MessageNotice notice = requireLockedNotice(tenantId, noticeId);
		if (!NoticeStatus.DRAFT.name().equals(notice.getStatus())) {
			throw new ArynBusinessException("只有草稿通知可以发布");
		}
		Set<MessageIdentityType> targetTypes = parseTargetTypes(notice.getTargetTypes());
		if (targetTypes.isEmpty()) {
			throw new ArynBusinessException("通知目标端不能为空");
		}
		notice.setStatus(NoticeStatus.PUBLISHED.name());
		notice.setPublishTime(LocalDateTime.now());
		notice.setUpdateBy(operatorId);
		notice.setUpdateTime(LocalDateTime.now());
		noticeMapper.updateById(notice);

		List<String> taskIds = new ArrayList<>();
		for (MessageIdentityType targetType : targetTypes) {
			MessageDispatchTask task = new MessageDispatchTask();
			task.setId(IdWorker.getIdStr());
			task.setTenantId(tenantId);
			task.setMessageId(noticeId);
			task.setTargetType(targetType.name());
			task.setAudienceType(resolveAudienceType(targetType, notice.getAudienceSnapshot()));
			task.setAudienceCondition(notice.getAudienceSnapshot());
			task.setStatus(DispatchTaskStatus.PENDING.name());
			task.setEstimatedCount(0L);
			task.setSuccessCount(0L);
			task.setFailureCount(0L);
			task.setRetryCount(0);
			task.setCreateBy(operatorId);
			task.setCreateTime(LocalDateTime.now());
			task.setDelFlag(CommonConstants.NO);
			dispatchTaskMapper.insert(task);
			taskIds.add(task.getId());
		}
		runAfterCommit(() -> taskIds.forEach(taskId -> dispatchService.dispatch(tenantId, taskId)));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void revoke(String tenantId, String operatorId, String noticeId) {
		MessageNotice notice = requireLockedNotice(tenantId, noticeId);
		if (NoticeStatus.REVOKED.name().equals(notice.getStatus())) {
			return;
		}
		if (!NoticeStatus.PUBLISHED.name().equals(notice.getStatus())) {
			throw new ArynBusinessException("只有已发布通知可以撤回");
		}
		notice.setStatus(NoticeStatus.REVOKED.name());
		notice.setUpdateBy(operatorId);
		notice.setUpdateTime(LocalDateTime.now());
		noticeMapper.updateById(notice);
		// 撤销尚未开始的任务，运行中的任务由分发循环在下一页检查到撤回后自行取消
		dispatchTaskMapper.cancelPendingTasks(tenantId, noticeId, operatorId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void republish(String tenantId, String operatorId, String noticeId) {
		MessageNotice notice = requireLockedNotice(tenantId, noticeId);
		if (!NoticeStatus.REVOKED.name().equals(notice.getStatus())) {
			throw new ArynBusinessException("只有已撤回通知可以重新发布");
		}
		if (notice.getExpireTime() != null && !notice.getExpireTime().isAfter(LocalDateTime.now())) {
			throw new ArynBusinessException("通知已过期，无法重新发布");
		}
		List<MessageDispatchTask> tasks = dispatchTaskMapper.selectByMessageId(tenantId, noticeId);
		if (tasks.stream().anyMatch(task -> DispatchTaskStatus.RUNNING.name().equals(task.getStatus()))) {
			throw new ArynBusinessException("仍有分发任务在运行，请稍后再重新发布");
		}
		notice.setStatus(NoticeStatus.PUBLISHED.name());
		notice.setPublishTime(LocalDateTime.now());
		notice.setUpdateBy(operatorId);
		notice.setUpdateTime(LocalDateTime.now());
		noticeMapper.updateById(notice);

		List<String> taskIds = tasks.stream()
			.filter(task -> !DispatchTaskStatus.SUCCEEDED.name().equals(task.getStatus()))
			.map(MessageDispatchTask::getId)
			.toList();
		if (taskIds.isEmpty()) {
			return;
		}
		if (dispatchTaskMapper.resetTasksForRepublish(tenantId, taskIds, operatorId) == 0) {
			throw new ArynBusinessException("分发任务状态已变化，请刷新后重试");
		}
		runAfterCommit(() -> taskIds.forEach(taskId -> dispatchService.dispatch(tenantId, taskId)));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void deleteDraft(String tenantId, String operatorId, String noticeId) {
		MessageNotice notice = requireLockedNotice(tenantId, noticeId);
		if (!NoticeStatus.DRAFT.name().equals(notice.getStatus())) {
			throw new ArynBusinessException("只有草稿通知可以删除");
		}
		if (noticeMapper.logicDeleteDraft(tenantId, noticeId, operatorId) == 0) {
			throw new ArynBusinessException("通知不存在或状态已变化");
		}
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void retryDispatch(String tenantId, String operatorId, String noticeId) {
		MessageNotice notice = requireLockedNotice(tenantId, noticeId);
		if (!NoticeStatus.PUBLISHED.name().equals(notice.getStatus())) {
			throw new ArynBusinessException("只有已发布通知可以重试分发");
		}
		List<MessageDispatchTask> failedTasks = dispatchTaskMapper.selectByMessageId(tenantId, noticeId).stream()
			.filter(task -> DispatchTaskStatus.FAILED.name().equals(task.getStatus())
					|| DispatchTaskStatus.PARTIAL_FAILED.name().equals(task.getStatus()))
			.toList();
		if (failedTasks.isEmpty()) {
			throw new ArynBusinessException("没有可重试的分发任务");
		}
		List<String> taskIds = failedTasks.stream().map(MessageDispatchTask::getId).toList();
		// 专用 SQL 重置：MyBatis-Plus 默认不更新 null 字段，error_summary 必须显式置空
		if (dispatchTaskMapper.resetFailedTasks(tenantId, taskIds, operatorId) == 0) {
			throw new ArynBusinessException("没有可重试的分发任务");
		}
		runAfterCommit(() -> taskIds.forEach(taskId -> dispatchService.dispatch(tenantId, taskId)));
	}

	@Override
	public NoticeInboxPageVO inbox(String tenantId, MessageIdentityType identityType, String identityId,
			NoticeInboxQuery query) {
		int limit = normalizeLimit(query == null ? null : query.getLimit());
		String cursor = query == null ? null : query.getCursor();
		List<NoticeInboxItemVO> records = recipientMapper.selectInbox(tenantId, identityType.name(), identityId,
				cursor, limit + 1);
		boolean hasMore = records.size() > limit;
		if (hasMore) {
			records = new ArrayList<>(records.subList(0, limit));
		}
		NoticeInboxPageVO result = new NoticeInboxPageVO();
		result.setRecords(records);
		result.setHasMore(hasMore);
		result.setNextCursor(hasMore && !records.isEmpty() ? records.get(records.size() - 1).getRecipientRecordId() : null);
		return result;
	}

	@Override
	public NoticeInboxItemVO inboxDetail(String tenantId, MessageIdentityType identityType, String identityId,
			String noticeId) {
		NoticeInboxItemVO detail = recipientMapper.selectInboxDetail(tenantId, identityType.name(), identityId,
				noticeId);
		if (detail == null) {
			throw new ArynBusinessException("通知不存在或无权访问");
		}
		return detail;
	}

	@Override
	public long unreadCount(String tenantId, MessageIdentityType identityType, String identityId) {
		return recipientMapper.countUnread(tenantId, identityType.name(), identityId);
	}

	@Override
	public void markRead(String tenantId, MessageIdentityType identityType, String identityId, String noticeId) {
		if (recipientMapper.markRead(tenantId, identityType.name(), identityId, noticeId) == 0) {
			throw new ArynBusinessException("通知不存在或无权访问");
		}
	}

	@Override
	public void markAllRead(String tenantId, MessageIdentityType identityType, String identityId) {
		recipientMapper.markAllRead(tenantId, identityType.name(), identityId);
	}

	@Override
	public void hide(String tenantId, MessageIdentityType identityType, String identityId, String noticeId) {
		if (recipientMapper.hide(tenantId, identityType.name(), identityId, noticeId) == 0) {
			throw new ArynBusinessException("通知不存在或无权访问");
		}

	}

	private MessageNotice requireLockedNotice(String tenantId, String noticeId) {
		MessageNotice notice = noticeMapper.selectByIdForUpdate(tenantId, noticeId);
		if (notice == null) {
			throw new ArynBusinessException("通知不存在");
		}
		return notice;
	}

	private void validateRequest(NoticeSaveRequest request) {
		if (request == null) {
			throw new ArynBusinessException("通知内容不能为空");
		}
		if (request.getTargetTypes() == null || request.getTargetTypes().isEmpty()
				|| request.getTargetTypes().contains(MessageIdentityType.SYSTEM)) {
			throw new ArynBusinessException("通知目标端仅支持会员或工作人员");
		}
		String payload = request.getJumpPayload();
		if (StringUtils.hasText(payload)) {
			String normalized = payload.toLowerCase(Locale.ROOT);
			if (normalized.contains("http://") || normalized.contains("https://") || normalized.contains("\\\"//")) {
				throw new ArynBusinessException("通知跳转只允许安全的站内路由或业务标识");
			}
		}
		if (request.getJumpType() == null || request.getJumpType() == NoticeJumpType.NONE) {
			request.setJumpType(NoticeJumpType.NONE);
			request.setJumpPayload(null);
		}
	}

	private void applyRequest(MessageNotice notice, NoticeSaveRequest request) {
		notice.setTitle(request.getTitle().trim());
		notice.setSummary(request.getSummary());
		notice.setContent(request.getContent());
		notice.setCategory(request.getCategory());
		notice.setPriority(StringUtils.hasText(request.getPriority()) ? request.getPriority() : "NORMAL");
		notice.setTargetTypes(request.getTargetTypes().stream()
			.sorted(Comparator.comparing(Enum::name))
			.map(Enum::name)
			.collect(Collectors.joining(",")));
		NoticeAudienceSnapshot audience = new NoticeAudienceSnapshot();
		audience.setMallUserIds(request.getMallUserIds());
		audience.setMemberLevelId(request.getMemberLevelId());
		audience.setMemberTagId(request.getMemberTagId());
		audience.setStaffUserIds(request.getStaffUserIds());
		audience.setRoleIds(request.getRoleIds());
		audience.setDeptIds(request.getDeptIds());
		notice.setAudienceSnapshot(writeJson(audience));
		notice.setExpireTime(request.getExpireTime());
		notice.setJumpType(request.getJumpType().name());
		notice.setJumpPayload(request.getJumpPayload());
	}

	private Set<MessageIdentityType> parseTargetTypes(String targetTypes) {
		if (!StringUtils.hasText(targetTypes)) {
			return Set.of();
		}
		return List.of(targetTypes.split(",")).stream()
			.map(MessageIdentityType::valueOf)
			.filter(type -> type != MessageIdentityType.SYSTEM)
			.collect(Collectors.toSet());
	}

	private String resolveAudienceType(MessageIdentityType targetType, String audienceSnapshot) {
		NoticeAudienceSnapshot snapshot = readAudience(audienceSnapshot);
		if (targetType == MessageIdentityType.MALL_USER) {
			if (snapshot.getMallUserIds() != null && !snapshot.getMallUserIds().isEmpty()) {
				return "SPECIFIED";
			}
			if (StringUtils.hasText(snapshot.getMemberLevelId()) || StringUtils.hasText(snapshot.getMemberTagId())) {
				return "FILTERED";
			}
			return "ALL";
		}
		if (snapshot.getStaffUserIds() != null && !snapshot.getStaffUserIds().isEmpty()) {
			return "SPECIFIED";
		}
		if ((snapshot.getRoleIds() != null && !snapshot.getRoleIds().isEmpty())
				|| (snapshot.getDeptIds() != null && !snapshot.getDeptIds().isEmpty())) {
			return "FILTERED";
		}
		return "ALL";
	}

	private NoticeAudienceSnapshot readAudience(String json) {
		try {
			return objectMapper.readValue(json, NoticeAudienceSnapshot.class);
		}
		catch (JsonProcessingException exception) {
			throw new ArynBusinessException("通知受众快照格式错误");
		}
	}

	private String writeJson(Object value) {
		try {
			return objectMapper.writeValueAsString(value);
		}
		catch (JsonProcessingException exception) {
			throw new ArynBusinessException("通知受众快照保存失败");
		}
	}

	private void enrichDelivery(List<NoticeVO> records, String tenantId) {
		if (records.isEmpty()) {
			return;
		}
		List<String> messageIds = records.stream().map(NoticeVO::getId).toList();
		Map<String, List<MessageDispatchTask>> tasksByMessage = dispatchTaskMapper
			.selectByMessageIds(tenantId, messageIds)
			.stream()
			.collect(Collectors.groupingBy(MessageDispatchTask::getMessageId));
		Map<String, NoticeRecipientStatsVO> statsByMessage = recipientMapper
			.selectStatsByMessageIds(tenantId, messageIds)
			.stream()
			.collect(Collectors.toMap(NoticeRecipientStatsVO::getMessageId, stats -> stats));
		for (NoticeVO record : records) {
			List<MessageDispatchTask> tasks = tasksByMessage.getOrDefault(record.getId(), List.of());
			record.setDispatchTasks(tasks.stream().map(this::toDispatchTaskVO).toList());
			record.setDispatchStatus(resolveDispatchStatus(record.getStatus(), tasks).name());
			NoticeRecipientStatsVO stats = statsByMessage.get(record.getId());
			record.setRecipientCount(stats == null ? 0L : valueOrZero(stats.getRecipientCount()));
			record.setReadCount(stats == null ? 0L : valueOrZero(stats.getReadCount()));
			record.setHiddenCount(stats == null ? 0L : valueOrZero(stats.getHiddenCount()));
		}
	}

	private NoticeDispatchStatus resolveDispatchStatus(String noticeStatus, List<MessageDispatchTask> tasks) {
		if (NoticeStatus.DRAFT.name().equals(noticeStatus) || tasks.isEmpty()) {
			return NoticeDispatchStatus.NONE;
		}
		boolean running = tasks.stream().anyMatch(task -> DispatchTaskStatus.PENDING.name().equals(task.getStatus())
				|| DispatchTaskStatus.RUNNING.name().equals(task.getStatus()));
		if (running) {
			return NoticeDispatchStatus.RUNNING;
		}
		long succeeded = tasks.stream()
			.filter(task -> DispatchTaskStatus.SUCCEEDED.name().equals(task.getStatus()))
			.count();
		if (succeeded == 0 && tasks.stream()
			.allMatch(task -> DispatchTaskStatus.CANCELED.name().equals(task.getStatus()))) {
			return NoticeDispatchStatus.NONE;
		}
		boolean unsuccessful = tasks.stream()
			.anyMatch(task -> DispatchTaskStatus.FAILED.name().equals(task.getStatus())
					|| DispatchTaskStatus.PARTIAL_FAILED.name().equals(task.getStatus())
					|| DispatchTaskStatus.CANCELED.name().equals(task.getStatus()));
		if (unsuccessful) {
			return succeeded > 0 ? NoticeDispatchStatus.PARTIAL_FAILED : NoticeDispatchStatus.FAILED;
		}
		return succeeded == tasks.size() ? NoticeDispatchStatus.SUCCEEDED : NoticeDispatchStatus.NONE;
	}

	private NoticeDispatchTaskVO toDispatchTaskVO(MessageDispatchTask task) {
		NoticeDispatchTaskVO vo = new NoticeDispatchTaskVO();
		BeanUtils.copyProperties(task, vo);
		return vo;
	}

	private NoticeRecipientItemVO toRecipientVO(MessageRecipient recipient) {
		NoticeRecipientItemVO vo = new NoticeRecipientItemVO();
		vo.setRecipientRecordId(recipient.getId());
		vo.setRecipientType(recipient.getRecipientType());
		vo.setRecipientId(recipient.getRecipientId());
		vo.setRecipientName(recipient.getRecipientName());
		vo.setReadStatus(recipient.getReadStatus());
		vo.setReadTime(recipient.getReadTime());
		vo.setReceivedTime(recipient.getReceivedTime());
		vo.setInboxStatus(recipient.getInboxStatus());
		return vo;
	}

	private long valueOrZero(Long value) {
		return value == null ? 0L : value;
	}

	private NoticeVO toVO(MessageNotice notice) {
		NoticeVO vo = new NoticeVO();
		BeanUtils.copyProperties(notice, vo);
		return vo;
	}

	private int normalizeLimit(Integer requestedLimit) {
		if (requestedLimit == null || requestedLimit <= 0) {
			return DEFAULT_INBOX_LIMIT;
		}
		return Math.min(requestedLimit, MAX_INBOX_LIMIT);
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
