package com.aryn.cloud.message.api.vo.notice;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 本人通知收件箱游标页。 */
@Data
public class NoticeInboxPageVO implements Serializable {

	private List<NoticeInboxItemVO> records;
	private String nextCursor;
	private boolean hasMore;

}
