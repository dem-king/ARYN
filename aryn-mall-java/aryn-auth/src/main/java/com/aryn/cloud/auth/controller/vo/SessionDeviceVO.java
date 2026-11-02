package com.aryn.cloud.auth.controller.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 当前账号的登录设备（会话）视图。
 *
 * <p>安全约束：{@code id} 是 token 的 SHA-256 前 12 位，不可反推、不可重放；
 * 不返回 token、ipAddr、tenantId、loginId。前端用它调「下线该设备」接口。</p>
 */
@Data
@Schema(description = "登录设备会话")
public class SessionDeviceVO {

	@Schema(description = "会话标识（token SHA-256 前 12 位）")
	private String id;

	@Schema(description = "设备类型：TOB/TOC")
	private String deviceType;

	@Schema(description = "浏览器")
	private String browser;

	@Schema(description = "操作系统")
	private String os;

	@Schema(description = "登录地点（仅城市/地区）")
	private String location;

	@Schema(description = "登录时间（ISO8601）")
	private String loginTime;

	@Schema(description = "是否当前设备")
	private boolean current;

}
