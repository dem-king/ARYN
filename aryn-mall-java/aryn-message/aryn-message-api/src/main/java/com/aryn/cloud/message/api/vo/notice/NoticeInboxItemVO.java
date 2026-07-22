package com.aryn.cloud.message.api.vo.notice;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 本人通知收件箱条目。 */
@Data
public class NoticeInboxItemVO implements Serializable {

	private String recipientRecordId;
	private String messageId;
	private String title;
	private String summary;
	private String content;
	private String category;
	private String sourceType;
	private String priority;
	private String senderName;
	private String readStatus;
	private LocalDateTime readTime;
	private LocalDateTime receivedTime;
	private LocalDateTime publishTime;
	private LocalDateTime expireTime;
	private String cardPayload;
	private String jumpType;
	private String jumpPayload;

}
