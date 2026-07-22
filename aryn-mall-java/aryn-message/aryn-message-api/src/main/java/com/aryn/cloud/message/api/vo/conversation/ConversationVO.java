package com.aryn.cloud.message.api.vo.conversation;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 会话视图。 */
@Data
public class ConversationVO implements Serializable {

	private String id;
	private String conversationType;
	private String queueCode;
	private String customerId;
	private String assignedStaffId;
	private String status;
	private Long lastSeq;
	private Long lastReadSeq;
	private Long unreadCount;
	private String lastMessageSummary;
	private LocalDateTime lastMessageTime;
	private LocalDateTime closedTime;
	private LocalDateTime reopenDeadline;
	private String contextPayload;

}
