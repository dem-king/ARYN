package com.aryn.cloud.message.api.vo.conversation;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 本人会话游标页。 */
@Data
public class ConversationInboxPageVO implements Serializable {

	private List<ConversationVO> records;
	private String nextCursor;
	private boolean hasMore;

}
