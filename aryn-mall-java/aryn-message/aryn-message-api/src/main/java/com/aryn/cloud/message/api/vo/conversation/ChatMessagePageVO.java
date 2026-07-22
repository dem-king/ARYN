package com.aryn.cloud.message.api.vo.conversation;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 会话消息游标页。 */
@Data
public class ChatMessagePageVO implements Serializable {

	private List<ChatMessageVO> records;
	private Long nextBeforeSeq;
	private Long nextAfterSeq;
	private boolean hasMore;

}
