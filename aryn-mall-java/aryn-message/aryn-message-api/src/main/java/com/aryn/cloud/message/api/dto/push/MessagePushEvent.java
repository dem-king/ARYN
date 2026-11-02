package com.aryn.cloud.message.api.dto.push;

import lombok.Data;

import java.io.Serializable;
import java.util.Map;

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

	// ---- 以下字段仅 SESSION_INVALID（三期：会话失效主动推送）事件使用 ----

	/** 会话失效原因 wire 值（BE_REPLACED / KICK_OUT / TOKEN_EXPIRED …） */
	private String reason;

	/** 踢下线细分原因 wire 值（仅 KICK_OUT 时出现） */
	private String subReason;

	/** 失效时间（ISO8601） */
	private String invalidAt;

	/** 被顶下线时的新设备信息（只含 os/browser/location/loginTime） */
	private Map<String, String> device;

}
