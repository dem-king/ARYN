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
import com.aryn.cloud.message.service.AgentService;
import com.aryn.cloud.message.service.ChatMessageService;
import com.aryn.cloud.message.service.MessagePushService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 会话消息幂等、排序和游标读取实现。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {

	private static final int DEFAULT_LIMIT = 30;
	private static final int MAX_LIMIT = 100;
	private static final Set<ChatMessageType> CLIENT_TYPES = Set.of(ChatMessageType.TEXT, ChatMessageType.IMAGE,
			ChatMessageType.PRODUCT_CARD, ChatMessageType.ORDER_CARD, ChatMessageType.REFUND_CARD,
			ChatMessageType.NOTICE_CARD);
	private static final Set<String> IMAGE_MIME_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
	// uniapp 当前把上传返回的完整 URL 当 fileId 传（域名+路径可能较长），放宽到 512，仍限制非空/长度。
	private static final int MAX_FILE_ID_LENGTH = 512;
	private static final int MAX_URL_LENGTH = 2048;
	private static final int MAX_BUSINESS_ID_LENGTH = 64;
	private static final int MAX_TITLE_LENGTH = 200;
	private static final int MAX_SUMMARY_LENGTH = 500;
	private static final int MAX_CARD_AMOUNT_LENGTH = 64;
	private static final int MAX_CARD_STATUS_TEXT_LENGTH = 64;
	/** 撤回时间窗：发送后 2 分钟内可撤回（产品建议值，集中定义便于调整口径）。 */
	private static final Duration RECALL_WINDOW = Duration.ofMinutes(2);
	/** 仅文本/图片可撤回：业务卡片是跨端结构化业务凭证，撤回会破坏订单/退款等业务语义。 */
	private static final Set<ChatMessageType> RECALLABLE_TYPES = Set.of(ChatMessageType.TEXT, ChatMessageType.IMAGE);
	/** 撤回后会话列表摘要占位：避免最后一条消息被撤回后列表预览仍展示原文。 */
	private static final String RECALLED_SUMMARY = "[消息已撤回]";
	private static final int MAX_QUOTED_MESSAGE_ID_LENGTH = 32;
	private static final int MAX_QUOTE_SUMMARY_LENGTH = 100;

	private final MessageConversationMapper conversationMapper;
	private final MessageParticipantMapper participantMapper;
	private final MessageChatMapper chatMapper;
	private final ObjectMapper objectMapper;
	private final MessagePushService pushService;
	private final MessageRateLimiter rateLimiter;
	private final AgentService agentService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ChatMessageVO send(String tenantId, MessageIdentityType senderType, String senderId, String senderName,
			String senderAvatar, String conversationId, ChatMessageSendRequest request) {
		validateClientMessage(tenantId, request);
		// 频控放在幂等查询和写库之前：重试同一 clientMessageId 也计入频控，避免无限重放。
		rateLimiter.assertSendAllowed(tenantId, senderType.name(), senderId, conversationId);
		MessageChat existing = chatMapper.selectByClientMessageId(tenantId, senderType.name(), senderId,
				request.getClientMessageId());
		if (existing != null) {
			assertSameConversation(existing, conversationId);
			return viewWithQuote(tenantId, conversationId, existing);
		}
		MessageConversation conversation = requireWritableConversation(tenantId, conversationId, senderType, senderId);
		existing = chatMapper.selectByClientMessageId(tenantId, senderType.name(), senderId,
				request.getClientMessageId());
		if (existing != null) {
			assertSameConversation(existing, conversationId);
			return viewWithQuote(tenantId, conversationId, existing);
		}
		// 引用目标校验放在会话/幂等校验之后：幂等重放历史消息不应因引用目标随后撤回而失败。
		MessageChat quoted = resolveQuotedMessage(tenantId, conversationId, request.getQuotedMessageId());
		return append(conversation, senderType, senderId, senderName, senderAvatar, request.getMessageType(),
				request.getContent(), request.getPayload(), request.getClientMessageId(), quoted);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public ChatMessageVO sendSystem(String tenantId, String conversationId, String content, String payload,
			String clientMessageId) {
		// 系统消息由服务端构造，沿用宽松的 JSON 校验（只要求结构合法且含 code）。
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
	@Transactional(rollbackFor = Exception.class)
	public ChatMessageVO recall(String tenantId, MessageIdentityType senderType, String senderId, String conversationId,
			String messageId) {
		if (!StringUtils.hasText(messageId)) {
			throw new ArynBusinessException("消息不存在或已删除");
		}
		// 与发送路径保持同一把锁顺序（先锁会话行，再读消息行），避免并发发送/撤回互相等待。
		MessageConversation conversation = conversationMapper.selectByIdForUpdate(tenantId, conversationId);
		if (conversation == null) {
			throw new ArynBusinessException("会话不存在");
		}
		if (ConversationStatus.CLOSED.name().equals(conversation.getStatus())) {
			throw new ArynBusinessException("已关闭会话不能撤回消息");
		}
		if (participantMapper.selectActiveParticipant(tenantId, conversationId, senderType.name(), senderId) == null) {
			throw new ArynBusinessException("无权撤回该会话消息");
		}
		MessageChat chat = findMessage(tenantId, conversationId, messageId);
		if (chat == null) {
			throw new ArynBusinessException("消息不存在或已删除");
		}
		if (!senderType.name().equals(chat.getSenderType()) || !senderId.equals(chat.getSenderId())) {
			throw new ArynBusinessException("只能撤回自己发送的消息");
		}
		if (CommonConstants.YES.equals(chat.getRecallStatus())) {
			// 重复撤回幂等成功：不再校验时间窗（撤回本身已生效）。
			return viewWithQuote(tenantId, conversationId, chat);
		}
		ChatMessageType messageType = parseMessageType(chat.getMessageType());
		if (messageType == null || !RECALLABLE_TYPES.contains(messageType)) {
			throw new ArynBusinessException("消息类型不支持撤回（仅文本和图片）");
		}
		LocalDateTime now = LocalDateTime.now();
		if (chat.getCreateTime() == null || now.isAfter(chat.getCreateTime().plus(RECALL_WINDOW))) {
			throw new ArynBusinessException("已超过撤回时间（2 分钟内可撤回）");
		}
		if (chatMapper.markRecalled(tenantId, conversationId, messageId, senderType.name(), senderId, now) == 0) {
			// 条件更新 0 行：并发双击/已被撤回 → 幂等成功；其它情况视为失败。
			MessageChat latest = findMessage(tenantId, conversationId, messageId);
			if (latest != null && CommonConstants.YES.equals(latest.getRecallStatus())) {
				return viewWithQuote(tenantId, conversationId, latest);
			}
			throw new ArynBusinessException("撤回失败，请重试");
		}
		chat.setRecallStatus(CommonConstants.YES);
		chat.setRecallTime(now);
		if (messageId.equals(conversation.getLastMessageId())) {
			// 撤回最后一条消息时同步改会话摘要：DB 保留原文审计，但列表预览不能继续展示撤回正文。
			conversation.setLastMessageSummary(RECALLED_SUMMARY);
			conversation.setUpdateBy(senderId);
			conversation.setUpdateTime(now);
			conversationMapper.updateById(conversation);
		}
		runAfterCommit(() -> pushService.pushConversationMessage(conversation.getTenantId(), conversation.getId(),
				chat.getId(), chat.getSeqNo() == null ? 0L : chat.getSeqNo()));
		return viewWithQuote(tenantId, conversationId, chat);
	}

	@Override
	public ChatMessagePageVO page(String tenantId, MessageIdentityType identityType, String identityId,
			String conversationId, ChatMessageCursorQuery query) {
		MessageParticipant participant = participantMapper.selectActiveParticipant(tenantId, conversationId,
				identityType.name(), identityId);
		if (participant == null) {
			// WAITING 会话允许启用坐席只读预览（不产生已读游标、不改变会话），CLOSED 会话仍只允许参与者读取。
			requireWaitingPreviewAccess(tenantId, identityType, identityId, conversationId);
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
		// 引用摘要批量补全：每页固定最多 1 条 IN 查询，避免逐条查询造成 N+1。
		List<ChatMessageVO> views = new ArrayList<>(rows.stream().map(this::toVO).toList());
		fillQuoteSummaries(tenantId, conversationId, views);
		result.setRecords(views);
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

	/**
	 * WAITING 会话的只读预览门禁：工作人员身份 + 会话处于 WAITING + 调用者是启用坐席。
	 *
	 * <p>只读预览不推进 last_read_seq、不改会话状态，避免未领取前产生「已读」语义。
	 */
	private void requireWaitingPreviewAccess(String tenantId, MessageIdentityType identityType, String identityId,
			String conversationId) {
		if (identityType != MessageIdentityType.SYS_USER) {
			throw new ArynBusinessException("会话不存在或无权访问");
		}
		MessageConversation conversation = conversationMapper.selectTenantById(tenantId, conversationId);
		if (conversation == null || !ConversationStatus.WAITING.name().equals(conversation.getStatus())) {
			throw new ArynBusinessException("会话不存在或无权访问");
		}
		if (!agentService.isEnabledAgent(tenantId, identityId)) {
			throw new ArynBusinessException("会话不存在或无权访问");
		}
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
			String clientMessageId, MessageChat quotedMessage) {
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
		chat.setQuotedMessageId(quotedMessage == null ? null : quotedMessage.getId());
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
		// 发送方显然已读自己的消息；不推进游标会让管理端铃铛把自己的回复算成未读。
		// 系统消息没有参与者行，此处自然空转。
		participantMapper.updateLastReadSeq(conversation.getTenantId(), conversation.getId(), senderType.name(),
				senderId, nextSeq);
		boolean waiting = ConversationStatus.WAITING.name().equals(conversation.getStatus());
		runAfterCommit(() -> {
			pushService.pushConversationMessage(conversation.getTenantId(), conversation.getId(), chat.getId(),
					chat.getSeqNo());
			if (waiting) {
				// 未分配会话没有坐席参与者，必须额外广播给共享池，否则调度台不知道有人排队。
				pushService.pushConversationQueued(conversation.getTenantId(), conversation.getId(), chat.getId(),
						chat.getSeqNo());
			}
		});
		ChatMessageVO view = toVO(chat);
		fillQuote(view, quotedMessage);
		return view;
	}

	/**
	 * 引用目标校验：空白视为未引用；必须同会话存在、未撤回、非系统消息。
	 *
	 * <p>校验放在幂等重放之后：同一 clientMessageId 的历史消息再次提交不因引用目标随后撤回而失败。</p>
	 */
	private MessageChat resolveQuotedMessage(String tenantId, String conversationId, String quotedMessageId) {
		if (!StringUtils.hasText(quotedMessageId)) {
			return null;
		}
		String normalized = quotedMessageId.trim();
		if (normalized.length() > MAX_QUOTED_MESSAGE_ID_LENGTH) {
			throw new ArynBusinessException("引用消息ID不合法");
		}
		MessageChat quoted = findMessage(tenantId, conversationId, normalized);
		if (quoted == null) {
			throw new ArynBusinessException("引用的消息不存在或不属于当前会话");
		}
		if (CommonConstants.YES.equals(quoted.getRecallStatus())) {
			throw new ArynBusinessException("引用的消息已撤回，不能引用");
		}
		if (ChatMessageType.SYSTEM.name().equals(quoted.getMessageType())) {
			throw new ArynBusinessException("系统消息不支持引用");
		}
		return quoted;
	}

	/** 按「租户 + 会话 + 消息 ID」查单条消息（引用校验/撤回共用，限定同会话）。 */
	private MessageChat findMessage(String tenantId, String conversationId, String messageId) {
		return chatMapper.selectByConversationAndIds(tenantId, conversationId, List.of(messageId))
			.stream()
			.findFirst()
			.orElse(null);
	}

	/** 单条消息转 VO 并补引用摘要（幂等重放/撤回返回使用）。 */
	private ChatMessageVO viewWithQuote(String tenantId, String conversationId, MessageChat chat) {
		ChatMessageVO view = toVO(chat);
		fillQuoteSummaries(tenantId, conversationId, List.of(view));
		return view;
	}

	/**
	 * 批量补引用摘要：一次 IN 查询取回当前页所有引用目标（page ≤100 条，固定 1 条额外 SQL）。
	 *
	 * <p>查不到的目标（历史脏数据/跨会话引用/已逻辑删除）按「引用不可用」降级，不抛错。</p>
	 */
	private void fillQuoteSummaries(String tenantId, String conversationId, List<ChatMessageVO> views) {
		if (views == null || views.isEmpty()) {
			return;
		}
		Set<String> quotedIds = views.stream()
			.map(ChatMessageVO::getQuotedMessageId)
			.filter(StringUtils::hasText)
			.collect(Collectors.toSet());
		if (quotedIds.isEmpty()) {
			return;
		}
		Map<String, MessageChat> targets = new HashMap<>();
		for (MessageChat target : chatMapper.selectByConversationAndIds(tenantId, conversationId, quotedIds)) {
			targets.put(target.getId(), target);
		}
		for (ChatMessageVO view : views) {
			if (StringUtils.hasText(view.getQuotedMessageId())) {
				fillQuote(view, targets.get(view.getQuotedMessageId()));
			}
		}
	}

	/** 回填单条引用摘要：目标已撤回/已删除/查不到时只标记 quotedRecalled=true。 */
	private void fillQuote(ChatMessageVO view, MessageChat target) {
		if (!StringUtils.hasText(view.getQuotedMessageId())) {
			return;
		}
		if (target == null || CommonConstants.YES.equals(target.getRecallStatus())) {
			view.setQuotedRecalled(Boolean.TRUE);
			return;
		}
		view.setQuotedMessageType(target.getMessageType());
		view.setQuotedSenderName(target.getSenderName());
		view.setQuotedMessageSummary(quoteSummary(target));
		view.setQuotedRecalled(Boolean.FALSE);
	}

	/** 引用摘要：文本截断 100 字符，图片/卡片用类型标签，卡片尽量附 title；读时生成不落库。 */
	private String quoteSummary(MessageChat target) {
		ChatMessageType type = parseMessageType(target.getMessageType());
		if (type == null) {
			return "[消息]";
		}
		String summary = switch (type) {
			case TEXT -> StringUtils.hasText(target.getContent()) ? target.getContent().trim() : "[文本]";
			case IMAGE -> "[图片]";
			case PRODUCT_CARD -> quoteCardSummary("[商品]", target.getPayload());
			case ORDER_CARD -> quoteCardSummary("[订单]", target.getPayload());
			case REFUND_CARD -> quoteCardSummary("[退款]", target.getPayload());
			case NOTICE_CARD -> quoteCardSummary("[通知]", target.getPayload());
			case SYSTEM -> "[系统消息]";
		};
		return summary.length() <= MAX_QUOTE_SUMMARY_LENGTH ? summary
				: summary.substring(0, MAX_QUOTE_SUMMARY_LENGTH);
	}

	private String quoteCardSummary(String label, String payload) {
		String title = payloadTitle(payload);
		return StringUtils.hasText(title) ? label + " " + title : label;
	}

	/** 从卡片 payload 取 title（坏 JSON/非文本一律忽略，引用摘要降级为类型标签）。 */
	private String payloadTitle(String payload) {
		if (!StringUtils.hasText(payload)) {
			return null;
		}
		try {
			JsonNode node = objectMapper.readTree(payload);
			JsonNode title = node == null ? null : node.get("title");
			return title != null && title.isTextual() && StringUtils.hasText(title.asText()) ? title.asText().trim()
					: null;
		}
		catch (JsonProcessingException exception) {
			return null;
		}
	}

	/** 消息类型安全解析：历史未知类型不抛 500，由调用方按「不支持/通用」降级。 */
	private ChatMessageType parseMessageType(String messageType) {
		if (!StringUtils.hasText(messageType)) {
			return null;
		}
		try {
			return ChatMessageType.valueOf(messageType);
		}
		catch (IllegalArgumentException exception) {
			return null;
		}
	}

	private void validateClientMessage(String tenantId, ChatMessageSendRequest request) {
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
		validatePayload(tenantId, request.getPayload(), request.getMessageType());
	}

	/** 客户端结构化负载严格校验；系统消息走宽松分支（{@link #validatePayload(String, ChatMessageType)}）。 */
	private void validatePayload(String tenantId, String payload, ChatMessageType messageType) {
		JsonNode node = readPayloadObject(payload);
		switch (messageType) {
			case IMAGE -> validateImagePayload(tenantId, node);
			case PRODUCT_CARD -> validateCardPayload(tenantId, node, "productId");
			case ORDER_CARD -> validateCardPayload(tenantId, node, "orderId");
			case REFUND_CARD -> validateCardPayload(tenantId, node, "refundId");
			case NOTICE_CARD -> validateCardPayload(tenantId, node, "noticeId");
			default -> throw new ArynBusinessException("不支持的结构化消息类型");
		}
	}

	/** 系统消息负载宽松校验：结构合法且必须带 code。 */
	private void validatePayload(String payload, ChatMessageType messageType) {
		JsonNode node = readPayloadObject(payload);
		if (messageType == ChatMessageType.SYSTEM) {
			requireText(node, "code");
		}
	}

	private JsonNode readPayloadObject(String payload) {
		if (!StringUtils.hasText(payload)) {
			throw new ArynBusinessException("结构化消息负载不能为空");
		}
		try {
			JsonNode node = objectMapper.readTree(payload);
			if (node == null || !node.isObject()) {
				throw new ArynBusinessException("结构化消息负载必须是 JSON 对象");
			}
			return node;
		}
		catch (JsonProcessingException exception) {
			throw new ArynBusinessException("结构化消息负载不是合法 JSON");
		}
	}

	/**
	 * IMAGE 负载：mimeType 仅 jpeg/png/webp；url 只允许 http(s) 或站内相对路径，
	 * 且路径必须包含 /{tenantId}/（只允许引用本租户上传的文件）；fileId 必填且限长。
	 */
	private void validateImagePayload(String tenantId, JsonNode node) {
		String fileId = requireText(node, "fileId");
		if (fileId.length() > MAX_FILE_ID_LENGTH) {
			throw new ArynBusinessException("图片 fileId 过长");
		}
		String url = requireText(node, "url");
		validateTenantFileUrl(tenantId, url, "图片地址");
		String mimeType = requireText(node, "mimeType").toLowerCase(Locale.ROOT);
		if (!IMAGE_MIME_TYPES.contains(mimeType)) {
			throw new ArynBusinessException("图片 MIME 类型不合法");
		}
	}

	/**
	 * 业务卡片负载：业务 ID 必填限长；title/summary/amount/statusText 限长；
	 * 可选 image 走与图片消息相同的 URL 规则；拒绝任意 url / link 字段。
	 */
	private void validateCardPayload(String tenantId, JsonNode node, String businessIdField) {
		requireText(node, businessIdField, MAX_BUSINESS_ID_LENGTH);
		requireOptionalTextWithin(node, "title", MAX_TITLE_LENGTH);
		requireOptionalTextWithin(node, "summary", MAX_SUMMARY_LENGTH);
		requireOptionalTextWithin(node, "amount", MAX_CARD_AMOUNT_LENGTH);
		requireOptionalTextWithin(node, "statusText", MAX_CARD_STATUS_TEXT_LENGTH);
		// 业务卡片只允许 image 一个 URL 字段，其它任意 URL 一律拒绝（防钓鱼跳转/外链注入）。
		if (node.hasNonNull("url") || node.hasNonNull("link") || node.hasNonNull("redirectUrl")) {
			throw new ArynBusinessException("卡片消息不支持任意 URL 字段");
		}
		JsonNode image = node.get("image");
		if (image != null && !image.isNull()) {
			if (!image.isTextual()) {
				throw new ArynBusinessException("卡片图片地址格式错误");
			}
			// 前端 `image: firstItem?.picUrl || ''` 会传空串，空串视为未提供（image 是可选字段）。
			if (StringUtils.hasText(image.asText())) {
				validateTenantFileUrl(tenantId, image.asText(), "卡片图片地址");
			}
		}
	}

	private String requireText(JsonNode node, String field) {
		return requireText(node, field, Integer.MAX_VALUE);
	}

	private String requireText(JsonNode node, String field, int maxLength) {
		JsonNode value = node.get(field);
		if (value == null || !value.isTextual() || !StringUtils.hasText(value.asText())) {
			throw new ArynBusinessException("结构化消息缺少字段: " + field);
		}
		String text = value.asText();
		if (text.length() > maxLength) {
			throw new ArynBusinessException("结构化消息字段过长: " + field);
		}
		return text;
	}

	private void requireOptionalTextWithin(JsonNode node, String field, int maxLength) {
		JsonNode value = node.get(field);
		if (value == null || value.isNull()) {
			return;
		}
		if (!value.isTextual()) {
			throw new ArynBusinessException("结构化消息字段格式错误: " + field);
		}
		if (value.asText().length() > maxLength) {
			throw new ArynBusinessException("结构化消息字段过长: " + field);
		}
	}

	/**
	 * 校验上传文件 URL：
	 * <ul>
	 *   <li>只允许 http(s) 绝对地址或站内相对路径（/ 开头）；</li>
	 *   <li>拒绝 javascript:/data:/vbscript: 等伪协议与协议相对地址（//）；</li>
	 *   <li>路径必须包含 /{tenantId}/，防止把其它租户的文件链接透传到会话里。</li>
	 * </ul>
	 */
	private void validateTenantFileUrl(String tenantId, String rawUrl, String fieldLabel) {
		String url = rawUrl.trim();
		if (!StringUtils.hasText(url) || url.length() > MAX_URL_LENGTH) {
			throw new ArynBusinessException(fieldLabel + "长度不合法");
		}
		String lower = url.toLowerCase(Locale.ROOT);
		if (lower.startsWith("javascript:") || lower.startsWith("data:") || lower.startsWith("vbscript:")) {
			throw new ArynBusinessException(fieldLabel + "协议不合法");
		}
		String path;
		if (lower.startsWith("http://") || lower.startsWith("https://")) {
			int schemeEnd = lower.indexOf("://") + 3;
			int pathStart = lower.indexOf('/', schemeEnd);
			if (pathStart < 0) {
				throw new ArynBusinessException(fieldLabel + "必须指向当前租户的上传文件");
			}
			path = url.substring(pathStart);
		}
		else if (url.startsWith("//")) {
			throw new ArynBusinessException(fieldLabel + "不支持协议相对地址");
		}
		else if (url.startsWith("/")) {
			path = url;
		}
		else {
			throw new ArynBusinessException(fieldLabel + "仅支持 http(s) 或站内相对路径");
		}
		// 只看路径部分，允许查询串（例如图片处理参数）；query 里出现 /tenant/ 不能当作合法路径。
		String pathOnly = path.split("[?#]", 2)[0];
		if (!pathOnly.contains("/" + tenantId + "/")) {
			throw new ArynBusinessException(fieldLabel + "必须指向当前租户的上传文件");
		}
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
		if (CommonConstants.YES.equals(chat.getRecallStatus())) {
			// 撤回后接口不再下发正文/结构化负载（DB 保留审计，前端按 recallStatus 渲染「消息已撤回」）。
			vo.setContent(null);
			vo.setPayload(null);
		}
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
