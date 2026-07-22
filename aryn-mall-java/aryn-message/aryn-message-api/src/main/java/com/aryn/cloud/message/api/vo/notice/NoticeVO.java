package com.aryn.cloud.message.api.vo.notice;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 通知管理视图。 */
@Data
public class NoticeVO implements Serializable {

	private String id;
	private String title;
	private String summary;
	private String content;
	private String category;
	private String sourceType;
	private String priority;
	private String targetTypes;
	private String status;
	private String senderName;
	private LocalDateTime publishTime;
	private LocalDateTime expireTime;
	private String cardPayload;
	private String jumpType;
	private String jumpPayload;
	private LocalDateTime createTime;

}
