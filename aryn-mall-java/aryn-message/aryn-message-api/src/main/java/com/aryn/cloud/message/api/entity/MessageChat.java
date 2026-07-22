package com.aryn.cloud.message.api.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 会话消息。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message_chat")
public class MessageChat extends AbstractMessageEntity {

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
	private LocalDateTime recallTime;

}
