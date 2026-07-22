package com.aryn.cloud.message.api.vo.conversation;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 会话消息视图。 */
@Data
public class ChatMessageVO implements Serializable {

	private String id;
	private String conversationId;
	private Long seqNo;
	private String senderType;
	private String senderId;
	private String senderName;
	private String senderAvatar;
	private String messageType;
	private String content;
	private String payload;
	private String clientMessageId;
	private String quotedMessageId;
	private String recallStatus;
	private LocalDateTime createTime;

}
