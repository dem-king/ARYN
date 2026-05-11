package com.aryn.cloud.common.core.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SysLoginLogBase {

	private String id;

	private String ipAddr;

	private String location;

	private String userName;

	private String status;

	private String msg;

	private String browser;

	private String os;

	private String createBy;

	private LocalDateTime createTime;

	private String delFlag;

	private String tenantId;

}
