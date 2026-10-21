package com.aryn.cloud.message.api.vo.notice;

import lombok.Data;

import java.io.Serializable;

/** 单条通知的收件、已读与隐藏统计。 */
@Data
public class NoticeRecipientStatsVO implements Serializable {

	private String messageId;
	private Long recipientCount;
	private Long readCount;
	private Long hiddenCount;

}
