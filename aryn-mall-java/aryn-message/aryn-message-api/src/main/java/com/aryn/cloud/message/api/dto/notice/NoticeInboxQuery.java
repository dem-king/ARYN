package com.aryn.cloud.message.api.dto.notice;

import lombok.Data;

import java.io.Serializable;

/** 本人通知收件箱游标查询。 */
@Data
public class NoticeInboxQuery implements Serializable {

	private String cursor;
	private Integer limit;

}
