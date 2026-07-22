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

	private String quotedMessageId;

}
