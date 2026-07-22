package com.aryn.cloud.user.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 会员受众游标页。 */
@Data
public class UserMessageAudiencePageVO implements Serializable {

	private List<UserMessageRecipientVO> records;
	private String nextCursor;
	private boolean hasMore;

}
