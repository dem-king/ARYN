package com.aryn.cloud.message.api.dto.push;

import lombok.Data;

import java.io.Serializable;

/** WebSocket/Redis 使用的轻量消息索引事件，不包含正文。 */
@Data
public class MessagePushEvent implements Serializable {

	private String eventType;
	private String tenantId;
	private String recipientType;
	private String recipientId;
	private String conversationId;
	private String messageId;
	private Long seqNo;
	private String originInstanceId;

}
