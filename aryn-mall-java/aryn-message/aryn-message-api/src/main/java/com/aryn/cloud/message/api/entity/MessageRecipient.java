package com.aryn.cloud.message.api.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 通知收件人。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message_recipient")
public class MessageRecipient extends AbstractMessageEntity {

	private String messageId;
	private String recipientType;
	private String recipientId;
	private String recipientName;
	private String readStatus;
	private LocalDateTime readTime;
	private LocalDateTime receivedTime;
	private String inboxStatus;

}
