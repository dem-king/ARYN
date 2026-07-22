package com.aryn.cloud.message.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/** 跨业务域发送单收件人站内通知的最小命令。 */
@Data
public class MessageSendCommand implements Serializable {

	@NotBlank
	@Size(max = 128)
	private String eventId;

	@NotBlank
	private String tenantId;

	@NotBlank
	private String recipientType;

	@NotBlank
	private String recipientId;

	private String recipientName;

	@NotBlank
	@Size(max = 64)
	private String category;

	@NotBlank
	@Size(max = 200)
	private String title;

	@Size(max = 500)
	private String summary;

	@NotBlank
	@Size(max = 10000)
	private String content;

	@Size(max = 16)
	private String priority;

	@NotBlank
	@Size(max = 64)
	private String bizType;

	@NotBlank
	@Size(max = 128)
	private String bizId;

	@Size(max = 10000)
	private String cardPayload;

	@Size(max = 2000)
	private String jumpPayload;

}
