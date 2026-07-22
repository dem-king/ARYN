package com.aryn.cloud.message.api.dto.notice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/** 从通知发起回复会话请求。 */
@Data
public class NoticeReplyRequest implements Serializable {

	@NotBlank
	@Size(max = 64)
	private String clientMessageId;

}
