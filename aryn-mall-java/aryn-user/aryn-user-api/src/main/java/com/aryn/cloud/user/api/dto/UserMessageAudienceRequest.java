package com.aryn.cloud.user.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 消息通知会员受众游标请求。 */
@Data
public class UserMessageAudienceRequest implements Serializable {

	private String tenantId;
	private String cursor;
	private Integer limit;
	private List<String> userIds;
	private String memberLevelId;
	private String memberTagId;

}
