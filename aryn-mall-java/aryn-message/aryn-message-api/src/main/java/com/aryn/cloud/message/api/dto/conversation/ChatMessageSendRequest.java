package com.aryn.cloud.message.api.dto.conversation;

import com.aryn.cloud.message.api.enums.ChatMessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/** 会话消息发送请求。 */
@Data
public class ChatMessageSendRequest implements Serializable {

	@NotBlank
	@Size(max = 64)
	private String clientMessageId;

	@NotNull
	private ChatMessageType messageType;

	@Size(max = 10000)
	private String content;

	@Size(max = 10000)
	private String payload;

	/**
	 * 引用消息 ID：须属于当前会话、存在、未撤回且非系统消息；空白视为未引用。
	 *
	 * <p>服务端同时会把引用目标的类型/摘要/发送人写到响应 VO 的 quoted* 字段（读时动态生成，不落库）。</p>
	 */
	private String quotedMessageId;

}
