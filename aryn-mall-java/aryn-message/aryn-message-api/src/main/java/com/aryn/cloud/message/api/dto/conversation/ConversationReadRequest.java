package com.aryn.cloud.message.api.dto.conversation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/** 会话已读游标请求。 */
@Data
public class ConversationReadRequest implements Serializable {

	@NotNull
	@Min(0)
	private Long lastReadSeq;

}
