package com.aryn.cloud.message.api.vo.notice;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 通知管理视图中的单条收件记录。 */
@Data
public class NoticeRecipientItemVO implements Serializable {

	private String recipientRecordId;
	private String recipientType;
	private String recipientId;
	private String recipientName;
	private String readStatus;
	private LocalDateTime readTime;
	private LocalDateTime receivedTime;
	private String inboxStatus;

}
