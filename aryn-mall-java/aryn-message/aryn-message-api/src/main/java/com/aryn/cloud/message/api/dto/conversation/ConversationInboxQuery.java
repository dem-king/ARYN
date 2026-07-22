package com.aryn.cloud.message.api.dto.conversation;

import lombok.Data;

import java.io.Serializable;

/** 本人会话列表查询。 */
@Data
public class ConversationInboxQuery implements Serializable {

	private String cursor;
	private Integer limit;

}
