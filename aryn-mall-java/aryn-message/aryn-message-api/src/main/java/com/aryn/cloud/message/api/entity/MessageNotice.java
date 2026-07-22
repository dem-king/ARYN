package com.aryn.cloud.message.api.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 站内通知。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("message_notice")
public class MessageNotice extends AbstractMessageEntity {

	private String title;
	private String summary;
	private String content;
	private String category;
	private String priority;
	private String sourceType;
	private String sourceKey;
	private String targetTypes;
	private String audienceSnapshot;
	private String senderType;
	private String senderId;
	private String senderName;
	private String status;
	private LocalDateTime publishTime;
	private LocalDateTime expireTime;
	private String cardPayload;
	private String jumpType;
	private String jumpPayload;

}
