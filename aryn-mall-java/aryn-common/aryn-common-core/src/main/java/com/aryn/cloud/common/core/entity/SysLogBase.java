package com.aryn.cloud.common.core.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysLogBase {

	private String id;

	private String ipAddr;

	private String title;

	private String requestMethod;

	private String requestUri;

	private String requestParams;

	private Long requestTime;

	private String location;

	private String method;

	private String userName;

	private String status;

	private String exMsg;

	private String createBy;

	private LocalDateTime createTime;

	private String delFlag;

	private String tenantId;

}
