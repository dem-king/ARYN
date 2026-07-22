package com.aryn.cloud.message.service;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.message.api.dto.conversation.ChatMessageSendRequest;
import com.aryn.cloud.message.api.entity.MessageChat;
import com.aryn.cloud.message.api.entity.MessageConversation;
import com.aryn.cloud.message.api.entity.MessageParticipant;
import com.aryn.cloud.message.api.enums.ChatMessageType;
import com.aryn.cloud.message.api.enums.ConversationStatus;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.message.mapper.MessageChatMapper;
import com.aryn.cloud.message.mapper.MessageConversationMapper;
import com.aryn.cloud.message.mapper.MessageParticipantMapper;
import com.aryn.cloud.message.service.impl.ChatMessageServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatMessageServiceImplTest {

	private MessageConversationMapper conversationMapper;
	private MessageParticipantMapper participantMapper;
	private MessageChatMapper chatMapper;
	private ChatMessageServiceImpl service;

	@BeforeEach
	void setUp() {
		conversationMapper = mock(MessageConversationMapper.class);
		participantMapper = mock(MessageParticipantMapper.class);
		chatMapper = mock(MessageChatMapper.class);
		service = new ChatMessageServiceImpl(conversationMapper, participantMapper, chatMapper, new ObjectMapper(),
				mock(MessagePushService.class));
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

}
