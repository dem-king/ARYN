package com.aryn.cloud.upms.api.vo;

import lombok.Data;

import java.io.Serializable;

/** 派单所需的配送员最小快照。 */
@Data
public class DeliveryStaffVO implements Serializable {

	private static final long serialVersionUID = 1L;

	private String id;
	private String nickname;
	private String phone;
	private String avatar;
	private String deptId;

}
