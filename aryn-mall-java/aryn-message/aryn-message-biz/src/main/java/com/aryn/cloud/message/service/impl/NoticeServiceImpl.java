package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.dto.notice.NoticeAudienceSnapshot;
import com.aryn.cloud.message.api.dto.notice.NoticeInboxQuery;
import com.aryn.cloud.message.api.dto.notice.NoticeSaveRequest;
import com.aryn.cloud.message.api.entity.MessageDispatchTask;
import com.aryn.cloud.message.api.entity.MessageNotice;
import com.aryn.cloud.message.api.enums.DispatchTaskStatus;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.enums.NoticeJumpType;
import com.aryn.cloud.message.api.enums.NoticeStatus;
import com.aryn.cloud.message.api.vo.notice.NoticeInboxItemVO;
import com.aryn.cloud.message.api.vo.notice.NoticeInboxPageVO;
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
		return toVO(notice);
	}

	@Override
	public IPage<NoticeVO> page(String tenantId, String status, Page<?> page) {
		Page<MessageNotice> sourcePage = new Page<>(page.getCurrent(), page.getSize());
		IPage<MessageNotice> source = noticeMapper.selectPage(sourcePage, Wrappers.<MessageNotice>lambdaQuery()
			.eq(MessageNotice::getTenantId, tenantId)
			.eq(StringUtils.hasText(status), MessageNotice::getStatus, status)
			.orderByDesc(MessageNotice::getCreateTime));
		Page<NoticeVO> result = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
		result.setRecords(source.getRecords().stream().map(this::toVO).toList());
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
