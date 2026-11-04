package com.aryn.cloud.upms.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户可用性快照：认证侧据此判断是否允许签发新会话。
 */
@Data
@Schema(description = "租户可用性")
public class TenantAccessDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String tenantId;

	/** 1.平台租户（长期有效，登录侧始终放行）；2.普通租户 */
	private String tenantType;

	/** 0.正常；1.停用 */
	private String status;

	private LocalDateTime authBeginTime;

	private LocalDateTime authEndTime;

}
