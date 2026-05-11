
package com.aryn.cloud.upms.api.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Schema(description = "系统在线用户")
public class SysUserOnline implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "token")
	private String tokenId;

	@Schema(description = "token过期时间")
	private Long tokenTimeout;

	@Schema(description = "登录时间")
	private LocalDateTime loginTime;

	@Schema(description = "ip地址")
	private String ipAddr;

	@Schema(description = "登录地点")
	private String location;

	@Schema(description = "登录用户")
	private String userName;

	@Schema(description = "浏览器")
	private String browser;

	@Schema(description = "操作系统")
	private String os;

	@Schema(description = "租户ID")
	private String tenantId;

}
