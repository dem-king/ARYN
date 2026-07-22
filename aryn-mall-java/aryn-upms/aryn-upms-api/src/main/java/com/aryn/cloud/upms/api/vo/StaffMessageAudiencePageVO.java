package com.aryn.cloud.upms.api.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 工作人员受众游标页。 */
@Data
public class StaffMessageAudiencePageVO implements Serializable {

	private List<StaffMessageRecipientVO> records;
	private String nextCursor;
	private boolean hasMore;

}
