package com.aryn.cloud.message.api.dto.conversation;

import lombok.Data;

import java.io.Serializable;

/** 会话消息游标查询。 */
@Data
public class ChatMessageCursorQuery implements Serializable {

	private Long beforeSeq;
	private Long afterSeq;
	private Integer limit;

}
