package com.aryn.cloud.message.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.dto.conversation.ConversationInboxQuery;
import com.aryn.cloud.message.api.entity.MessageConversation;
import com.aryn.cloud.message.api.entity.MessageParticipant;
import com.aryn.cloud.message.api.enums.ConversationStatus;
import com.aryn.cloud.message.api.enums.ConversationType;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.api.vo.conversation.ConversationAssignmentLogVO;
import com.aryn.cloud.message.api.vo.conversation.ConversationAttentionVO;
import com.aryn.cloud.message.api.vo.conversation.ConversationInboxPageVO;
import com.aryn.cloud.message.api.vo.conversation.ConversationVO;
import com.aryn.cloud.message.api.vo.agent.AgentCapacityVO;
import com.aryn.cloud.message.api.vo.conversation.QueueStatsGroupVO;
import com.aryn.cloud.message.api.vo.conversation.QueueStatsTotalVO;
import com.aryn.cloud.message.api.vo.conversation.QueueStatsVO;
import com.aryn.cloud.message.mapper.MessageAssignmentLogMapper;
import com.aryn.cloud.message.mapper.MessageConversationMapper;
import com.aryn.cloud.message.mapper.MessageParticipantMapper;
import com.aryn.cloud.message.service.AgentService;
import com.aryn.cloud.message.service.ConversationService;
import com.aryn.cloud.message.service.ConversationAssignmentService;
import com.aryn.cloud.user.api.dto.UserMessageAudienceRequest;
import com.aryn.cloud.user.api.remote.RemoteMessageAudienceService;
import com.aryn.cloud.user.api.vo.UserMessageAudiencePageVO;
import com.aryn.cloud.user.api.vo.UserMessageRecipientVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** 会话创建、查询和已读实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

	private static final String DEFAULT_QUEUE = "DEFAULT";
	private static final int DEFAULT_LIMIT = 20;
	private static final int MAX_LIMIT = 100;
	/** keyword 最大长度（字符）：超长直接报参数错误，避免无意义超长 LIKE 扫描。 */
	private static final int MAX_KEYWORD_LENGTH = 64;
	/** LIKE 转义符：用 '!' 而非反斜杠，规避 MySQL 字符串转义方言差异（SQL 侧配 ESCAPE '!'）。 */
	private static final char LIKE_ESCAPE_CHAR = '!';
	private static final String STATUS_OPEN = "OPEN";
	private static final String STATUS_CLOSED = "CLOSED";
	private static final String CURSOR_SEPARATOR = "|";
	private static final ZoneId CURSOR_ZONE = ZoneId.systemDefault();

	private final MessageConversationMapper conversationMapper;
	private final MessageParticipantMapper participantMapper;
	private final MessageAssignmentLogMapper assignmentLogMapper;
	private final ObjectMapper objectMapper;
	private final ConversationAssignmentService assignmentService;
	private final AgentService agentService;

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
			// 已存在的 WAITING 会话的自动分配重试由接口层在事务提交后执行，
			// 避免内层 TransactionTemplate 与并发领取相互污染外层事务。
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
		InboxCriteria criteria = normalizeCriteria(query);
		InboxCursor cursor = criteria.cursor();
		List<ConversationVO> records = conversationMapper.selectInbox(tenantId, identityType.name(), identityId,
				criteria.status(), criteria.statuses(), criteria.keyword(), criteria.assignedStaffId(),
				criteria.queueCode(), criteria.unreadOnly(), cursor == null ? null : cursor.time(),
				cursor == null ? null : cursor.id(), criteria.limit() + 1);
		return toPage(records, criteria.limit());
	}

	@Override
	public ConversationInboxPageVO adminInbox(String tenantId, ConversationInboxQuery query) {
		InboxCriteria criteria = normalizeCriteria(query);
		InboxCursor cursor = criteria.cursor();
		List<ConversationVO> records = conversationMapper.selectTenantInbox(tenantId, criteria.status(),
				criteria.statuses(), criteria.keyword(), criteria.assignedStaffId(), criteria.queueCode(),
				criteria.unreadOnly(), cursor == null ? null : cursor.time(),
				cursor == null ? null : cursor.id(), criteria.limit() + 1);
		return toPage(records, criteria.limit());
	}

	@Override
	public QueueStatsVO queueStats(String tenantId) {
		List<QueueStatsGroupVO> groups = conversationMapper.selectWaitingGroups(tenantId);
		long waitingCount = 0L;
		LocalDateTime oldestWaitingSince = null;
		if (groups != null) {
			for (QueueStatsGroupVO group : groups) {
				waitingCount += group.getWaitingCount() == null ? 0L : group.getWaitingCount();
				LocalDateTime groupOldest = group.getOldestWaitingSince();
				if (groupOldest != null && (oldestWaitingSince == null || groupOldest.isBefore(oldestWaitingSince))) {
					oldestWaitingSince = groupOldest;
				}
			}
		}
		AgentCapacityVO capacity = agentService.agentCapacity(tenantId);
		QueueStatsTotalVO total = new QueueStatsTotalVO();
		total.setWaitingCount(waitingCount);
		total.setOldestWaitingSince(oldestWaitingSince);
		total.setOnlineAgentCount(capacity == null ? 0 : capacity.getOnlineAgentCount());
		total.setAvailableCapacity(capacity == null ? 0 : capacity.getAvailableCapacity());
		QueueStatsVO stats = new QueueStatsVO();
		stats.setTotal(total);
		stats.setGroups(groups == null ? List.of() : groups);
		return stats;
	}

	@Override
	public ConversationAttentionVO attention(String tenantId, MessageIdentityType identityType, String identityId,
			String queueCode, int limit) {
		int normalizedLimit = Math.min(Math.max(limit, 1), MAX_LIMIT);
		ConversationAttentionVO result = new ConversationAttentionVO();
		List<ConversationVO> conversations = new ArrayList<>(
				conversationMapper.selectUnreadForParticipant(tenantId, identityType.name(), identityId,
						normalizedLimit));
		result.setUnreadConversations(
				conversationMapper.countUnreadForParticipant(tenantId, identityType.name(), identityId));
		// 共享池待领取提醒只对「启用且 ONLINE」的坐席可见：接待状态只约束承接新工作。
		if (identityType == MessageIdentityType.SYS_USER && agentService.isAcceptingNewWork(tenantId, identityId)) {
			String normalizedQueue = StringUtils.hasText(queueCode) ? queueCode : DEFAULT_QUEUE;
			result.setWaitingTotal(conversationMapper.countWaiting(tenantId, normalizedQueue));
			// 共享池还没有坐席参与者，未读查询天然取不到，这里按同一顺序补在未读之后。
			appendWaiting(conversations, tenantId, normalizedQueue, normalizedLimit);
		}
		result.setConversations(conversations);
		return result;
	}

	@Override
	public List<ConversationAssignmentLogVO> assignmentLogs(String tenantId, String conversationId) {
		return assignmentLogMapper.selectByConversation(tenantId, conversationId);
	}

	/** 待领取会话对未读查询不可见（没有参与者行），需要显式补进提醒列表尾部。 */
	private void appendWaiting(List<ConversationVO> conversations, String tenantId, String queueCode, int limit) {
		if (conversations.size() >= limit) {
			return;
		}
		Set<String> known = conversations.stream().map(ConversationVO::getId).collect(Collectors.toSet());
		for (ConversationVO waiting : conversationMapper.selectWaitingPool(tenantId, queueCode, limit)) {
			if (conversations.size() >= limit) {
				return;
			}
			if (!known.add(waiting.getId())) {
				continue;
			}
			waiting.setUnreadCount(0L);
			waiting.setLastReadSeq(0L);
			conversations.add(waiting);
		}
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

	/** 状态过滤参数归一化：只接受 OPEN / CLOSED，缺省不过滤。 */
	private String normalizeStatus(String requested) {
		if (!StringUtils.hasText(requested)) {
			return null;
		}
		String status = requested.trim().toUpperCase(Locale.ROOT);
		if (!STATUS_OPEN.equals(status) && !STATUS_CLOSED.equals(status)) {
			throw new ArynBusinessException("status 仅支持 OPEN 或 CLOSED");
		}
		return status;
	}

	/** 列表筛选参数归一化：statuses 优先于 status；空串归 null；keyword 转义；游标解析；limit 归一化。 */
	private InboxCriteria normalizeCriteria(ConversationInboxQuery query) {
		ConversationInboxQuery requested = query == null ? new ConversationInboxQuery() : query;
		List<String> statuses = normalizeStatuses(requested.getStatuses());
		// statuses 是精确状态集合，非空时覆盖旧页签 status；同时传不报错，旧前端参数残留可平滑兼容。
		String status = statuses == null ? normalizeStatus(requested.getStatus()) : null;
		return new InboxCriteria(status, statuses, normalizeKeyword(requested.getKeyword()),
				trimToNull(requested.getAssignedStaffId()), trimToNull(requested.getQueueCode()),
				Boolean.TRUE.equals(requested.getUnreadOnly()), parseCursor(requested.getCursor()),
				normalizeLimit(requested.getLimit()));
	}

	/** 精确状态集合归一化：trim + 大写 + 去空 + 去重；非法值报参数错误；空集合返回 null（视为未传）。 */
	private List<String> normalizeStatuses(List<String> requested) {
		if (requested == null || requested.isEmpty()) {
			return null;
		}
		Set<String> normalized = new LinkedHashSet<>();
		for (String item : requested) {
			if (!StringUtils.hasText(item)) {
				continue;
			}
			String status = item.trim().toUpperCase(Locale.ROOT);
			if (!isConversationStatus(status)) {
				throw new ArynBusinessException("statuses 仅支持 WAITING/ASSIGNED/ACTIVE/CLOSED");
			}
			normalized.add(status);
		}
		return normalized.isEmpty() ? null : List.copyOf(normalized);
	}

	private boolean isConversationStatus(String status) {
		for (ConversationStatus candidate : ConversationStatus.values()) {
			if (candidate.name().equals(status)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * keyword 清洗：trim 后为空忽略；限长 64 字符；LIKE 通配符（! % _）按字面量转义。
	 *
	 * <p>转义符固定 '!' 且 SQL 侧固定 {@code ESCAPE '!'}，避免用户输入 %/_ 把包含匹配变成通配匹配。</p>
	 */
	private String normalizeKeyword(String keyword) {
		if (!StringUtils.hasText(keyword)) {
			return null;
		}
		String trimmed = keyword.trim();
		if (trimmed.length() > MAX_KEYWORD_LENGTH) {
			throw new ArynBusinessException("keyword 长度不能超过 64");
		}
		StringBuilder escaped = new StringBuilder(trimmed.length() + 8);
		for (int index = 0; index < trimmed.length(); index++) {
			char current = trimmed.charAt(index);
			if (current == LIKE_ESCAPE_CHAR || current == '%' || current == '_') {
				escaped.append(LIKE_ESCAPE_CHAR);
			}
			escaped.append(current);
		}
		return escaped.toString();
	}

	/** 精确过滤值清洗：trim 后为空视为未传。 */
	private String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	/**
	 * 解析不透明游标：Base64Url(最近活跃时间毫秒 | 会话 ID)。
	 *
	 * <p>游标对前端不透明，服务端内部编码排序键与 id，避免「最近活跃时间相同」时
	 * 仅按 id 翻页导致的漏项/重复。
	 */
	private InboxCursor parseCursor(String cursor) {
		if (!StringUtils.hasText(cursor)) {
			return null;
		}
		try {
			String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			int separator = decoded.indexOf(CURSOR_SEPARATOR);
			if (separator <= 0 || separator >= decoded.length() - 1) {
				throw new IllegalArgumentException("游标格式错误");
			}
			long millis = Long.parseLong(decoded.substring(0, separator));
			String id = decoded.substring(separator + 1);
			return new InboxCursor(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), CURSOR_ZONE), id);
		}
		catch (RuntimeException exception) {
			throw new ArynBusinessException("游标参数不合法");
		}
	}

	/** 编码下一页游标；排序键取 COALESCE(last_message_time, create_time)。 */
	private String encodeCursor(ConversationVO last) {
		LocalDateTime activeTime = last.getLastActiveTime() == null ? LocalDateTime.now() : last.getLastActiveTime();
		long millis = activeTime.atZone(CURSOR_ZONE).toInstant().toEpochMilli();
		String raw = millis + CURSOR_SEPARATOR + last.getId();
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	/** 已查询 limit+1 条的记录转成游标页。 */
	private ConversationInboxPageVO toPage(List<ConversationVO> records, int limit) {
		List<ConversationVO> pageRecords = records;
		boolean hasMore = pageRecords.size() > limit;
		if (hasMore) {
			pageRecords = new ArrayList<>(pageRecords.subList(0, limit));
		}
		ConversationInboxPageVO result = new ConversationInboxPageVO();
		result.setRecords(pageRecords);
		result.setHasMore(hasMore);
		result.setNextCursor(hasMore && !pageRecords.isEmpty() ? encodeCursor(pageRecords.get(pageRecords.size() - 1)) : null);
		return result;
	}

	private int normalizeLimit(Integer requested) {
		if (requested == null || requested <= 0) {
			return DEFAULT_LIMIT;
		}
		return Math.min(requested, MAX_LIMIT);
	}

	/** 复合游标内部结构：最近活跃时间 + 会话 ID。 */
	private record InboxCursor(LocalDateTime time, String id) {
	}

	/** 列表筛选归一化结果（inbox/adminInbox 共用，保证 staff 与主管两端口径一致）。 */
	private record InboxCriteria(String status, List<String> statuses, String keyword, String assignedStaffId,
			String queueCode, boolean unreadOnly, InboxCursor cursor, int limit) {
	}

}
