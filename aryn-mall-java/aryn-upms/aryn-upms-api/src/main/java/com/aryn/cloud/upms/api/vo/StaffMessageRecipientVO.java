package com.aryn.cloud.upms.api.vo;

import lombok.Data;

import java.io.Serializable;

/** 消息通知所需的最小工作人员快照。 */
@Data
public class StaffMessageRecipientVO implements Serializable {

	private String id;
	private String nickname;
	private String avatar;
	private String deptId;

}
