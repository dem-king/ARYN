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
import com.aryn.cloud.message.mapper.MessageChatMapper;
import com.aryn.cloud.message.mapper.MessageConversationMapper;
import com.aryn.cloud.message.mapper.MessageParticipantMapper;
import com.aryn.cloud.message.service.impl.ChatMessageServiceImpl;
import com.aryn.cloud.message.service.impl.MessageRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageServiceImplTest {

	private MessageConversationMapper conversationMapper;
	private MessageParticipantMapper participantMapper;
	private MessageChatMapper chatMapper;
	private MessagePushService pushService;
	private AgentService agentService;
	private ValueOperations<String, String> valueOperations;
	private ChatMessageServiceImpl service;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		conversationMapper = mock(MessageConversationMapper.class);
		participantMapper = mock(MessageParticipantMapper.class);
		chatMapper = mock(MessageChatMapper.class);
		pushService = mock(MessagePushService.class);
		agentService = mock(AgentService.class);
		StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
		valueOperations = mock(ValueOperations.class);
		when(redisTemplate.opsForValue()).thenReturn(valueOperations);
		// 测试里阈值压到 2，便于构造超限；Redis 计数默认返回 null（无计数）视为放行。
		MessageRateLimiter rateLimiter = new MessageRateLimiter(redisTemplate, 2, 2, 2);
		service = new ChatMessageServiceImpl(conversationMapper, participantMapper, chatMapper, new ObjectMapper(),
				pushService, rateLimiter, agentService);
	}

	@Test
	void sameClientMessageIdReturnsExistingMessage() {
		MessageChat existing = new MessageChat();
		existing.setId("message-1");
		existing.setConversationId("conversation-1");
		existing.setClientMessageId("client-1");
		when(chatMapper.selectByClientMessageId("tenant-1", "MALL_USER", "member-1", "client-1"))
			.thenReturn(existing);

		service.send("tenant-1", MessageIdentityType.MALL_USER, "member-1", "会员", null, "conversation-1",
				text("client-1", "你好"));

		verify(chatMapper, never()).insert(any(MessageChat.class));
		verify(conversationMapper, never()).selectByIdForUpdate(any(), any());
	}

	@Test
	void conversationSequenceIsStrictlyIncreasing() {
		MessageConversation conversation = conversation();
		when(conversationMapper.selectByIdForUpdate("tenant-1", "conversation-1")).thenReturn(conversation);
		when(participantMapper.selectActiveParticipant("tenant-1", "conversation-1", "MALL_USER", "member-1"))
			.thenReturn(new MessageParticipant());
		when(chatMapper.selectByClientMessageId(any(), any(), any(), any())).thenReturn(null);

		service.send("tenant-1", MessageIdentityType.MALL_USER, "member-1", "会员", null, "conversation-1",
				text("client-1", "第一条"));
		service.send("tenant-1", MessageIdentityType.MALL_USER, "member-1", "会员", null, "conversation-1",
				text("client-2", "第二条"));

		ArgumentCaptor<MessageChat> captor = ArgumentCaptor.forClass(MessageChat.class);
		verify(chatMapper, times(2)).insert(captor.capture());
		assertEquals(1L, captor.getAllValues().get(0).getSeqNo());
		assertEquals(2L, captor.getAllValues().get(1).getSeqNo());
		assertEquals(2L, conversation.getLastSeq());
	}

	@Test
	void nonParticipantCannotSend() {
		when(conversationMapper.selectByIdForUpdate("tenant-1", "conversation-1")).thenReturn(conversation());
		when(participantMapper.selectActiveParticipant("tenant-1", "conversation-1", "SYS_USER", "staff-2"))
			.thenReturn(null);

		assertThrows(ArynBusinessException.class,
				() -> service.send("tenant-1", MessageIdentityType.SYS_USER, "staff-2", "客服", null,
						"conversation-1", text("client-1", "越权")));
		verify(chatMapper, never()).insert(any(MessageChat.class));
	}

	@Test
	void clientCannotSendSystemMessage() {
		ChatMessageSendRequest request = text("client-1", "系统消息");
		request.setMessageType(ChatMessageType.SYSTEM);

		assertThrows(ArynBusinessException.class,
				() -> service.send("tenant-1", MessageIdentityType.MALL_USER, "member-1", "会员", null,
						"conversation-1", request));
	}

	@Test
	void rateLimitRejectsBeforeIdempotencyLookup() {
		when(valueOperations.increment(anyString())).thenReturn(3L);

		ArynBusinessException exception = assertThrows(ArynBusinessException.class,
				() -> service.send("tenant-1", MessageIdentityType.MALL_USER, "member-1", "会员", null,
						"conversation-1", text("client-1", "刷屏")));

		assertEquals("发送过于频繁，请稍后再试", exception.getMsg());
		// 契约要求频控发生在幂等查询和写库之前。
		verify(chatMapper, never()).selectByClientMessageId(any(), any(), any(), any());
		verify(chatMapper, never()).insert(any(MessageChat.class));
	}

	@Test
	void redisFailureDegradesToAllowSend() {
		when(valueOperations.increment(anyString())).thenThrow(new IllegalStateException("redis down"));
		MessageConversation conversation = conversation();
		when(conversationMapper.selectByIdForUpdate("tenant-1", "conversation-1")).thenReturn(conversation);
		when(participantMapper.selectActiveParticipant("tenant-1", "conversation-1", "MALL_USER", "member-1"))
			.thenReturn(new MessageParticipant());
		when(chatMapper.selectByClientMessageId(any(), any(), any(), any())).thenReturn(null);

		service.send("tenant-1", MessageIdentityType.MALL_USER, "member-1", "会员", null, "conversation-1",
				text("client-1", "Redis 挂了也要能发"));

		verify(chatMapper).insert(any(MessageChat.class));
	}

	@Test
	void imageWithNonWhitelistedMimeTypeIsRejected() {
		ChatMessageSendRequest request = image("client-1",
				"{\"fileId\":\"file-1\",\"url\":\"/file/local/tenant-1/a.gif\",\"mimeType\":\"image/gif\"}");

		assertThrows(ArynBusinessException.class,
				() -> service.send("tenant-1", MessageIdentityType.MALL_USER, "member-1", "会员", null,
						"conversation-1", request));
	}

	@Test
	void imageUrlMustBeTenantScopedAndSafe() {
		assertImageRejected("{\"fileId\":\"file-1\",\"url\":\"javascript:alert(1)\",\"mimeType\":\"image/png\"}");
		assertImageRejected("{\"fileId\":\"file-1\",\"url\":\"data:image/png;base64,AAA\",\"mimeType\":\"image/png\"}");
		assertImageRejected("{\"fileId\":\"file-1\",\"url\":\"//cdn.example.com/a.png\",\"mimeType\":\"image/png\"}");
		assertImageRejected("{\"fileId\":\"file-1\",\"url\":\"https://cdn.example.com/other-tenant/a.png\",\"mimeType\":\"image/png\"}");
		assertImageRejected("{\"fileId\":\"file-1\",\"url\":\"https://cdn.example.com/a.png\",\"mimeType\":\"image/png\"}");
		// fileId 必填且限长
		assertImageRejected(
				"{\"fileId\":\"" + "f".repeat(513) + "\",\"url\":\"/file/local/tenant-1/a.png\",\"mimeType\":\"image/png\"}");
	}

	@Test
	void validTenantScopedImageIsAccepted() {
		MessageConversation conversation = conversation();
		when(conversationMapper.selectByIdForUpdate("tenant-1", "conversation-1")).thenReturn(conversation);
		when(participantMapper.selectActiveParticipant("tenant-1", "conversation-1", "MALL_USER", "member-1"))
			.thenReturn(new MessageParticipant());
		when(chatMapper.selectByClientMessageId(any(), any(), any(), any())).thenReturn(null);

		service.send("tenant-1", MessageIdentityType.MALL_USER, "member-1", "会员", null, "conversation-1",
				image("client-1",
						"{\"fileId\":\"file-1\",\"url\":\"https://cdn.example.com/file/local/tenant-1/a.png\",\"mimeType\":\"image/jpeg\"}"));

		verify(chatMapper).insert(any(MessageChat.class));
	}

	@Test
	void cardBusinessIdIsBoundedAndMustBePresent() {
		ChatMessageSendRequest tooLong = card("client-1", ChatMessageType.PRODUCT_CARD,
				"{\"productId\":\"" + "p".repeat(65) + "\"}");
		assertThrows(ArynBusinessException.class,
				() -> service.send("tenant-1", MessageIdentityType.MALL_USER, "member-1", "会员", null,
						"conversation-1", tooLong));

		ChatMessageSendRequest missing = card("client-1", ChatMessageType.ORDER_CARD, "{\"title\":\"订单\"}");
		assertThrows(ArynBusinessException.class,
				() -> service.send("tenant-1", MessageIdentityType.MALL_USER, "member-1", "会员", null,
						"conversation-1", missing));
	}

	@Test
	void cardImageUsesSameUrlRulesAndArbitraryUrlIsRejected() {
		ChatMessageSendRequest unsafeImage = card("client-1", ChatMessageType.ORDER_CARD,
				"{\"orderId\":\"o-1\",\"image\":\"https://evil.example.com/a.png\"}");
		assertThrows(ArynBusinessException.class,
				() -> service.send("tenant-1", MessageIdentityType.MALL_USER, "member-1", "会员", null,
						"conversation-1", unsafeImage));

		ChatMessageSendRequest arbitraryUrl = card("client-1", ChatMessageType.NOTICE_CARD,
				"{\"noticeId\":\"n-1\",\"url\":\"https://evil.example.com\"}");
		assertThrows(ArynBusinessException.class,
				() -> service.send("tenant-1", MessageIdentityType.MALL_USER, "member-1", "会员", null,
						"conversation-1", arbitraryUrl));

		ChatMessageSendRequest textTooLong = card("client-1", ChatMessageType.REFUND_CARD,
				"{\"refundId\":\"r-1\",\"title\":\"" + "标".repeat(201) + "\"}");
		assertThrows(ArynBusinessException.class,
				() -> service.send("tenant-1", MessageIdentityType.MALL_USER, "member-1", "会员", null,
						"conversation-1", textTooLong));
	}

	@Test
	void waitingConversationAllowsEnabledAgentReadOnlyPreview() {
		MessageConversation conversation = conversation();
		conversation.setStatus(ConversationStatus.WAITING.name());
		when(participantMapper.selectActiveParticipant("tenant-1", "conversation-1", "SYS_USER", "staff-1"))
			.thenReturn(null);
		when(conversationMapper.selectTenantById("tenant-1", "conversation-1")).thenReturn(conversation);
		when(agentService.isEnabledAgent("tenant-1", "staff-1")).thenReturn(true);
		when(chatMapper.selectCursorPage("tenant-1", "conversation-1", null, null, 31)).thenReturn(List.of());

		ChatMessagePageVO page = service.page("tenant-1", MessageIdentityType.SYS_USER, "staff-1",
				"conversation-1", new ChatMessageCursorQuery());

		assertEquals(0, page.getRecords().size());
		// 只读预览不得推进已读游标。
		verify(participantMapper, never()).updateLastReadSeq(any(), any(), any(), any(), anyLong());
	}

	@Test
	void waitingPreviewRejectedForDisabledAgentOrMember() {
		MessageConversation conversation = conversation();
		conversation.setStatus(ConversationStatus.WAITING.name());
		when(participantMapper.selectActiveParticipant("tenant-1", "conversation-1", "SYS_USER", "staff-1"))
			.thenReturn(null);
		when(conversationMapper.selectTenantById("tenant-1", "conversation-1")).thenReturn(conversation);
		when(agentService.isEnabledAgent("tenant-1", "staff-1")).thenReturn(false);

		assertThrows(ArynBusinessException.class, () -> service.page("tenant-1", MessageIdentityType.SYS_USER,
				"staff-1", "conversation-1", null));
		verify(chatMapper, never()).selectCursorPage(any(), any(), any(), any(), any(Integer.class));
	}

	@Test
	void closedConversationIsNotPreviewableByNonParticipant() {
		MessageConversation conversation = conversation();
		conversation.setStatus(ConversationStatus.CLOSED.name());
		when(participantMapper.selectActiveParticipant("tenant-1", "conversation-1", "SYS_USER", "staff-1"))
			.thenReturn(null);
		when(conversationMapper.selectTenantById("tenant-1", "conversation-1")).thenReturn(conversation);

		assertThrows(ArynBusinessException.class, () -> service.page("tenant-1", MessageIdentityType.SYS_USER,
				"staff-1", "conversation-1", null));
		verify(agentService, never()).isEnabledAgent(any(), any());
	}

	@Test
	void memberMessageInWaitingConversationNotifiesSharedPool() {
		MessageConversation conversation = conversation();
		conversation.setStatus(ConversationStatus.WAITING.name());
		when(conversationMapper.selectByIdForUpdate("tenant-1", "conversation-1")).thenReturn(conversation);
		when(participantMapper.selectActiveParticipant("tenant-1", "conversation-1", "MALL_USER", "member-1"))
			.thenReturn(new MessageParticipant());
		when(chatMapper.selectByClientMessageId(any(), any(), any(), any())).thenReturn(null);

		service.send("tenant-1", MessageIdentityType.MALL_USER, "member-1", "会员", null, "conversation-1",
				text("client-1", "有人在吗"));

		// 未分配会话没有坐席参与者，必须额外广播共享池，调度台才知道有人排队。
		verify(pushService).pushConversationQueued(eq("tenant-1"), eq("conversation-1"), any(), anyLong());
	}

	@Test
	void staffReplyDoesNotNotifySharedPool() {
		MessageConversation conversation = conversation();
		conversation.setStatus(ConversationStatus.ASSIGNED.name());
		when(conversationMapper.selectByIdForUpdate("tenant-1", "conversation-1")).thenReturn(conversation);
		when(participantMapper.selectActiveParticipant("tenant-1", "conversation-1", "SYS_USER", "staff-1"))
			.thenReturn(new MessageParticipant());
		when(chatMapper.selectByClientMessageId(any(), any(), any(), any())).thenReturn(null);

		service.send("tenant-1", MessageIdentityType.SYS_USER, "staff-1", "客服", null, "conversation-1",
				text("client-1", "您好"));

		verify(pushService, never()).pushConversationQueued(any(), any(), any(), anyLong());
	}

	private void assertImageRejected(String payload) {
		assertThrows(ArynBusinessException.class,
				() -> service.send("tenant-1", MessageIdentityType.MALL_USER, "member-1", "会员", null,
						"conversation-1", image("client-1", payload)));
	}

	private MessageConversation conversation() {
		MessageConversation conversation = new MessageConversation();
		conversation.setId("conversation-1");
		conversation.setTenantId("tenant-1");
		conversation.setStatus(ConversationStatus.WAITING.name());
		conversation.setLastSeq(0L);
		return conversation;
	}

	private ChatMessageSendRequest text(String clientMessageId, String content) {
		ChatMessageSendRequest request = new ChatMessageSendRequest();
		request.setClientMessageId(clientMessageId);
		request.setMessageType(ChatMessageType.TEXT);
		request.setContent(content);
		return request;
	}

	private ChatMessageSendRequest image(String clientMessageId, String payload) {
		return card(clientMessageId, ChatMessageType.IMAGE, payload);
	}

	private ChatMessageSendRequest card(String clientMessageId, ChatMessageType type, String payload) {
		ChatMessageSendRequest request = new ChatMessageSendRequest();
		request.setClientMessageId(clientMessageId);
		request.setMessageType(type);
		request.setPayload(payload);
		return request;
	}

}
