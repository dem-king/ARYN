package com.aryn.cloud.upms.api.dto;

import lombok.Data;

import java.io.Serializable;

/** 商城配送员候选游标查询。 */
@Data
public class DeliveryStaffQuery implements Serializable {

	private static final long serialVersionUID = 1L;

	private String tenantId;
	private String keyword;
	private String cursor;
	private Integer limit;

}
