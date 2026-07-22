package com.aryn.cloud.message.api.dto.notice;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 发布时冻结的受众条件。 */
@Data
public class NoticeAudienceSnapshot implements Serializable {

	private List<String> mallUserIds;
	private String memberLevelId;
	private String memberTagId;
	private List<String> staffUserIds;
	private List<String> roleIds;
	private List<String> deptIds;

}
