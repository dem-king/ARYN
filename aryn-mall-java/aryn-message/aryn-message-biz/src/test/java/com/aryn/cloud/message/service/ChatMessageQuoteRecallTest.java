package com.aryn.cloud.message.service;

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
import com.aryn.cloud.message.service.impl.ChatMessageServiceImpl;
import com.aryn.cloud.message.service.impl.MessageRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P2-A 批次二：消息引用（发送校验 + 摘要批量补全）与消息撤回契约测试。
 *
 * <p>锁住口径：引用目标同会话/未撤回/非系统消息；摘要读时生成且 page 只做一次批量查询（防 N+1）；
 * 撤回仅本人 TEXT/IMAGE、2 分钟内、会话未关闭、幂等；撤回后 content/payload 脱敏、
 * 最后一条消息撤回同步改会话摘要、事务提交后复用 CONVERSATION_MESSAGE 推送。</p>
 */
class ChatMessageQuoteRecallTest {

	private static final String TENANT = "tenant-1";
	private static final String CONVERSATION = "conversation-1";

	private MessageConversationMapper conversationMapper;
	private MessageParticipantMapper participantMapper;
	private MessageChatMapper chatMapper;
	private MessagePushService pushService;
	private ChatMessageServiceImpl service;

	@BeforeEach
	void setUp() {
		conversationMapper = mock(MessageConversationMapper.class);
		participantMapper = mock(MessageParticipantMapper.class);
		chatMapper = mock(MessageChatMapper.class);
		pushService = mock(MessagePushService.class);
		service = new ChatMessageServiceImpl(conversationMapper, participantMapper, chatMapper, new ObjectMapper(),
				pushService, mock(MessageRateLimiter.class), mock(AgentService.class));
	}

	// ---------------- 引用 ----------------

	@Test
	void sendPersistsQuotedMessageIdAndFillsSummary() {
		stubWritableConversation(ConversationStatus.ACTIVE, 5L);
		MessageChat quoted = quotedText("msg-q", "请问什么时候发货？", "王小雅");
		when(chatMapper.selectByConversationAndIds(eq(TENANT), eq(CONVERSATION), any())).thenReturn(List.of(quoted));

		ChatMessageVO view = service.send(TENANT, MessageIdentityType.SYS_USER, "staff-1", "客服小美", null,
				CONVERSATION, textRequest("client-1", "今天发出", "msg-q"));

		ArgumentCaptor<MessageChat> captor = ArgumentCaptor.forClass(MessageChat.class);
		verify(chatMapper).insert(captor.capture());
		assertEquals("msg-q", captor.getValue().getQuotedMessageId());
		assertEquals("msg-q", view.getQuotedMessageId());
		assertEquals("TEXT", view.getQuotedMessageType());
		assertEquals("请问什么时候发货？", view.getQuotedMessageSummary());
		assertEquals("王小雅", view.getQuotedSenderName());
		assertFalse(view.getQuotedRecalled());
	}

	@Test
	void blankQuotedMessageIdIsTreatedAsNoQuote() {
		stubWritableConversation(ConversationStatus.ACTIVE, 5L);

		ChatMessageVO view = service.send(TENANT, MessageIdentityType.SYS_USER, "staff-1", "客服小美", null,
				CONVERSATION, textRequest("client-1", "普通回复", "   "));

		ArgumentCaptor<MessageChat> captor = ArgumentCaptor.forClass(MessageChat.class);
		verify(chatMapper).insert(captor.capture());
		assertNull(captor.getValue().getQuotedMessageId());
		assertNull(view.getQuotedMessageId());
		verify(chatMapper, never()).selectByConversationAndIds(any(), any(), any());
	}

	@Test
	void quoteTargetMustExistInSameConversation() {
		stubWritableConversation(ConversationStatus.ACTIVE, 5L);
		when(chatMapper.selectByConversationAndIds(eq(TENANT), eq(CONVERSATION), any())).thenReturn(List.of());

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.send(TENANT, MessageIdentityType.SYS_USER, "staff-1", "客服小美", null, CONVERSATION,
						textRequest("client-1", "回复", "msg-other")));

		assertEquals("引用的消息不存在或不属于当前会话", exception.getMsg());
		verify(chatMapper, never()).insert(any(MessageChat.class));
	}

	@Test
	void quoteTargetCannotBeRecalledOrSystemMessage() {
		stubWritableConversation(ConversationStatus.ACTIVE, 5L);
		MessageChat recalled = quotedText("msg-r", "原文", "会员");
		recalled.setRecallStatus("1");
		when(chatMapper.selectByConversationAndIds(eq(TENANT), eq(CONVERSATION), any())).thenReturn(List.of(recalled));

		ArynBusinessException recalledError = assertThrows(ArynBusinessException.class,
				() -> service.send(TENANT, MessageIdentityType.SYS_USER, "staff-1", "客服小美", null, CONVERSATION,
						textRequest("client-1", "回复", "msg-r")));
		assertEquals("引用的消息已撤回，不能引用", recalledError.getMsg());

		MessageChat system = quotedText("msg-s", null, "系统");
		system.setMessageType(ChatMessageType.SYSTEM.name());
		when(chatMapper.selectByConversationAndIds(eq(TENANT), eq(CONVERSATION), any())).thenReturn(List.of(system));

		ArynBusinessException systemError = assertThrows(ArynBusinessException.class,
				() -> service.send(TENANT, MessageIdentityType.SYS_USER, "staff-1", "客服小美", null, CONVERSATION,
						textRequest("client-2", "回复", "msg-s")));
		assertEquals("系统消息不支持引用", systemError.getMsg());
	}

	@Test
	void quotedMessageIdMustRespectLength() {
		stubWritableConversation(ConversationStatus.ACTIVE, 5L);

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.send(TENANT, MessageIdentityType.SYS_USER, "staff-1", "客服小美", null, CONVERSATION,
						textRequest("client-1", "回复", "x".repeat(33))));

		assertEquals("引用消息ID不合法", exception.getMsg());
	}

	@Test
	void pageFillsQuoteSummariesWithSingleBatchQuery() {
		when(participantMapper.selectActiveParticipant(TENANT, CONVERSATION, "SYS_USER", "staff-1"))
			.thenReturn(new MessageParticipant());
		MessageChat longText = quotedText("msg-long", "很".repeat(120), "会员");
		MessageChat recalled = quotedText("msg-r", "原文", "会员");
		recalled.setRecallStatus("1");
		MessageChat first = message("msg-1", ChatMessageType.TEXT, "回复一");
		first.setQuotedMessageId("msg-long");
		MessageChat second = message("msg-2", ChatMessageType.TEXT, "回复二");
		second.setQuotedMessageId("msg-r");
		MessageChat plain = message("msg-3", ChatMessageType.TEXT, "普通消息");
		when(chatMapper.selectCursorPage(TENANT, CONVERSATION, null, null, 31))
			.thenReturn(List.of(first, second, plain));
		when(chatMapper.selectByConversationAndIds(eq(TENANT), eq(CONVERSATION), any()))
			.thenReturn(List.of(longText, recalled));

		ChatMessagePageVO page = service.page(TENANT, MessageIdentityType.SYS_USER, "staff-1", CONVERSATION,
				new ChatMessageCursorQuery());

		assertEquals(3, page.getRecords().size());
		// 文本摘要截断 100 字符。
		assertEquals(100, page.getRecords().get(0).getQuotedMessageSummary().length());
		assertFalse(page.getRecords().get(0).getQuotedRecalled());
		// 目标已撤回：只标记不可用，不下发摘要。
		assertTrue(page.getRecords().get(1).getQuotedRecalled());
		assertNull(page.getRecords().get(1).getQuotedMessageSummary());
		// 无引用：quotedRecalled 为 null。
		assertNull(page.getRecords().get(2).getQuotedRecalled());
		// 防 N+1：整页只做一次批量查询。
		verify(chatMapper, times(1)).selectByConversationAndIds(eq(TENANT), eq(CONVERSATION), any());
	}

	@Test
	void cardQuoteSummaryUsesTypeLabelAndTitle() {
		stubWritableConversation(ConversationStatus.ACTIVE, 5L);
		MessageChat card = quotedText("msg-card", null, "会员");
		card.setMessageType(ChatMessageType.ORDER_CARD.name());
		card.setPayload("{\"orderId\":\"o-1\",\"title\":\"订单 A123\"}");
		when(chatMapper.selectByConversationAndIds(eq(TENANT), eq(CONVERSATION), any())).thenReturn(List.of(card));

		ChatMessageVO view = service.send(TENANT, MessageIdentityType.SYS_USER, "staff-1", "客服小美", null,
				CONVERSATION, textRequest("client-1", "收到", "msg-card"));

		assertEquals("ORDER_CARD", view.getQuotedMessageType());
		assertEquals("[订单] 订单 A123", view.getQuotedMessageSummary());
	}

	// ---------------- 撤回 ----------------

	@Test
	void recallMasksContentUpdatesSummaryAndPushesMessageEvent() {
		MessageConversation conversation = stubConversationForRecall("msg-1");
		MessageChat chat = message("msg-1", ChatMessageType.TEXT, "发错了");
		chat.setSenderType("SYS_USER");
		chat.setSenderId("staff-1");
		chat.setSeqNo(8L);
		chat.setCreateTime(LocalDateTime.now().minusSeconds(30));
		when(chatMapper.selectByConversationAndIds(eq(TENANT), eq(CONVERSATION), any())).thenReturn(List.of(chat));
		when(chatMapper.markRecalled(eq(TENANT), eq(CONVERSATION), eq("msg-1"), eq("SYS_USER"), eq("staff-1"), any()))
			.thenReturn(1);

		ChatMessageVO view = service.recall(TENANT, MessageIdentityType.SYS_USER, "staff-1", CONVERSATION, "msg-1");

		assertEquals("1", view.getRecallStatus());
		assertNull(view.getContent());
		assertNull(view.getPayload());
		assertTrue(view.getRecallTime() != null);
		// 撤回最后一条消息：列表摘要改为占位，避免预览泄露正文。
		assertEquals("[消息已撤回]", conversation.getLastMessageSummary());
		verify(conversationMapper).updateById(conversation);
		// 事务提交后复用既有 CONVERSATION_MESSAGE 推送（单测无事务上下文时立即执行）。
		verify(pushService).pushConversationMessage(TENANT, CONVERSATION, "msg-1", 8L);
	}

	@Test
	void recallDoesNotTouchSummaryWhenNotLastMessage() {
		MessageConversation conversation = stubConversationForRecall("msg-9");
		MessageChat chat = message("msg-1", ChatMessageType.IMAGE, null);
		chat.setSenderType("SYS_USER");
		chat.setSenderId("staff-1");
		chat.setPayload("{\"fileId\":\"/f\",\"url\":\"/x\",\"mimeType\":\"image/png\"}");
		chat.setCreateTime(LocalDateTime.now().minusSeconds(10));
		when(chatMapper.selectByConversationAndIds(eq(TENANT), eq(CONVERSATION), any())).thenReturn(List.of(chat));
		when(chatMapper.markRecalled(eq(TENANT), eq(CONVERSATION), eq("msg-1"), eq("SYS_USER"), eq("staff-1"), any()))
			.thenReturn(1);

		ChatMessageVO view = service.recall(TENANT, MessageIdentityType.SYS_USER, "staff-1", CONVERSATION, "msg-1");

		assertEquals("1", view.getRecallStatus());
		assertNull(view.getPayload());
		verify(conversationMapper, never()).updateById(any(MessageConversation.class));
	}

	@Test
	void recallRejectsMissingMessage() {
		stubConversationForRecall(null);
		when(chatMapper.selectByConversationAndIds(eq(TENANT), eq(CONVERSATION), any())).thenReturn(List.of());

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.recall(TENANT, MessageIdentityType.SYS_USER, "staff-1", CONVERSATION, "msg-missing"));

		assertEquals("消息不存在或已删除", exception.getMsg());
		verify(chatMapper, never()).markRecalled(any(), any(), any(), any(), any(), any());
	}

	@Test
	void recallRejectsOtherStaffMessage() {
		stubConversationForRecall(null);
		MessageChat chat = message("msg-1", ChatMessageType.TEXT, "别人的消息");
		chat.setSenderType("SYS_USER");
		chat.setSenderId("staff-2");
		chat.setCreateTime(LocalDateTime.now().minusSeconds(10));
		when(chatMapper.selectByConversationAndIds(eq(TENANT), eq(CONVERSATION), any())).thenReturn(List.of(chat));

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.recall(TENANT, MessageIdentityType.SYS_USER, "staff-1", CONVERSATION, "msg-1"));

		assertEquals("只能撤回自己发送的消息", exception.getMsg());
		verify(chatMapper, never()).markRecalled(any(), any(), any(), any(), any(), any());
	}

	@Test
	void recallRejectsExpiredClosedUnsupportedAndNonParticipant() {
		stubConversationForRecall(null);
		MessageChat expired = message("msg-1", ChatMessageType.TEXT, "超时");
		expired.setSenderType("SYS_USER");
		expired.setSenderId("staff-1");
		expired.setCreateTime(LocalDateTime.now().minusMinutes(3));
		when(chatMapper.selectByConversationAndIds(eq(TENANT), eq(CONVERSATION), any())).thenReturn(List.of(expired));
		ArynBusinessException expiredError = assertThrows(ArynBusinessException.class,
				() -> service.recall(TENANT, MessageIdentityType.SYS_USER, "staff-1", CONVERSATION, "msg-1"));
		assertEquals("已超过撤回时间（2 分钟内可撤回）", expiredError.getMsg());

		MessageConversation closed = baseConversation();
		closed.setStatus(ConversationStatus.CLOSED.name());
		when(conversationMapper.selectByIdForUpdate(TENANT, CONVERSATION)).thenReturn(closed);
		ArynBusinessException closedError = assertThrows(ArynBusinessException.class,
				() -> service.recall(TENANT, MessageIdentityType.SYS_USER, "staff-1", CONVERSATION, "msg-1"));
		assertEquals("已关闭会话不能撤回消息", closedError.getMsg());

		stubConversationForRecall(null);
		MessageChat card = message("msg-1", ChatMessageType.ORDER_CARD, null);
		card.setSenderType("SYS_USER");
		card.setSenderId("staff-1");
		card.setPayload("{\"orderId\":\"o-1\"}");
		card.setCreateTime(LocalDateTime.now().minusSeconds(10));
		when(chatMapper.selectByConversationAndIds(eq(TENANT), eq(CONVERSATION), any())).thenReturn(List.of(card));
		ArynBusinessException typeError = assertThrows(ArynBusinessException.class,
				() -> service.recall(TENANT, MessageIdentityType.SYS_USER, "staff-1", CONVERSATION, "msg-1"));
		assertEquals("消息类型不支持撤回（仅文本和图片）", typeError.getMsg());

		when(conversationMapper.selectByIdForUpdate(TENANT, CONVERSATION)).thenReturn(baseConversation());
		when(participantMapper.selectActiveParticipant(TENANT, CONVERSATION, "SYS_USER", "staff-9")).thenReturn(null);
		ArynBusinessException permissionError = assertThrows(ArynBusinessException.class,
				() -> service.recall(TENANT, MessageIdentityType.SYS_USER, "staff-9", CONVERSATION, "msg-1"));
		assertEquals("无权撤回该会话消息", permissionError.getMsg());
	}

	@Test
	void recallIsIdempotentWhenAlreadyRecalled() {
		stubConversationForRecall(null);
		MessageChat recalled = message("msg-1", ChatMessageType.TEXT, null);
		recalled.setSenderType("SYS_USER");
		recalled.setSenderId("staff-1");
		recalled.setRecallStatus("1");
		recalled.setRecallTime(LocalDateTime.now().minusSeconds(5));
		when(chatMapper.selectByConversationAndIds(eq(TENANT), eq(CONVERSATION), any())).thenReturn(List.of(recalled));

		ChatMessageVO view = service.recall(TENANT, MessageIdentityType.SYS_USER, "staff-1", CONVERSATION, "msg-1");

		assertEquals("1", view.getRecallStatus());
		// 幂等：不重复条件更新、不再推送。
		verify(chatMapper, never()).markRecalled(any(), any(), any(), any(), any(), any());
		verify(pushService, never()).pushConversationMessage(any(), any(), any(), anyLong());
	}

	// ---------------- 图片 payload 与上传 URL 的发送兼容 ----------------

	@Test
	void sendImageAcceptsUploadedTenantScopedUrl() {
		// 与 MessageUploadServiceTest 中 mock 存储返回的 URL 形态一致：路径必含 /{tenantId}/。
		stubWritableConversation(ConversationStatus.ACTIVE, 5L);
		String url = "/upms/file/local/tenant-1/9f0f1c6e.png";
		String payload = "{\"fileId\":\"" + url + "\",\"url\":\"" + url + "\",\"mimeType\":\"image/png\"}";

		service.send(TENANT, MessageIdentityType.SYS_USER, "staff-1", "客服小美", null, CONVERSATION,
				imageRequest("client-img-1", payload));

		ArgumentCaptor<MessageChat> captor = ArgumentCaptor.forClass(MessageChat.class);
		verify(chatMapper).insert(captor.capture());
		assertEquals(ChatMessageType.IMAGE.name(), captor.getValue().getMessageType());
	}

	private MessageConversation stubWritableConversation(ConversationStatus status, long lastSeq) {
		MessageConversation conversation = baseConversation();
		conversation.setStatus(status.name());
		conversation.setLastSeq(lastSeq);
		when(conversationMapper.selectByIdForUpdate(TENANT, CONVERSATION)).thenReturn(conversation);
		when(participantMapper.selectActiveParticipant(TENANT, CONVERSATION, "SYS_USER", "staff-1"))
			.thenReturn(new MessageParticipant());
		when(chatMapper.selectByClientMessageId(any(), any(), any(), any())).thenReturn(null);
		return conversation;
	}

	private MessageConversation stubConversationForRecall(String lastMessageId) {
		MessageConversation conversation = baseConversation();
		conversation.setStatus(ConversationStatus.ACTIVE.name());
		conversation.setLastSeq(8L);
		conversation.setLastMessageId(lastMessageId);
		when(conversationMapper.selectByIdForUpdate(TENANT, CONVERSATION)).thenReturn(conversation);
		when(participantMapper.selectActiveParticipant(TENANT, CONVERSATION, "SYS_USER", "staff-1"))
			.thenReturn(new MessageParticipant());
		return conversation;
	}

	private MessageConversation baseConversation() {
		MessageConversation conversation = new MessageConversation();
		conversation.setId(CONVERSATION);
		conversation.setTenantId(TENANT);
		conversation.setLastSeq(0L);
		return conversation;
	}

	private MessageChat message(String id, ChatMessageType type, String content) {
		MessageChat chat = new MessageChat();
		chat.setId(id);
		chat.setConversationId(CONVERSATION);
		chat.setTenantId(TENANT);
		chat.setMessageType(type.name());
		chat.setContent(content);
		chat.setRecallStatus("0");
		chat.setSeqNo(1L);
		chat.setCreateTime(LocalDateTime.now().minusMinutes(1));
		return chat;
	}

	private MessageChat quotedText(String id, String content, String senderName) {
		MessageChat chat = message(id, ChatMessageType.TEXT, content);
		chat.setSenderType("MALL_USER");
		chat.setSenderId("member-1");
		chat.setSenderName(senderName);
		return chat;
	}

	private ChatMessageSendRequest textRequest(String clientMessageId, String content, String quotedMessageId) {
		ChatMessageSendRequest request = new ChatMessageSendRequest();
		request.setClientMessageId(clientMessageId);
		request.setMessageType(ChatMessageType.TEXT);
		request.setContent(content);
		request.setQuotedMessageId(quotedMessageId);
		return request;
	}

	private ChatMessageSendRequest imageRequest(String clientMessageId, String payload) {
		ChatMessageSendRequest request = new ChatMessageSendRequest();
		request.setClientMessageId(clientMessageId);
		request.setMessageType(ChatMessageType.IMAGE);
		request.setPayload(payload);
		return request;
	}

}
