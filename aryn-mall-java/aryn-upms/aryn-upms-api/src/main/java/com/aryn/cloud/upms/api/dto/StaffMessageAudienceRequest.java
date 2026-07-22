package com.aryn.cloud.upms.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 消息通知工作人员受众游标请求。 */
@Data
public class StaffMessageAudienceRequest implements Serializable {

	private String tenantId;
	private String cursor;
	private Integer limit;
	private List<String> userIds;
	private List<String> roleIds;
	private List<String> deptIds;
	private Boolean customerServiceOnly;

}
