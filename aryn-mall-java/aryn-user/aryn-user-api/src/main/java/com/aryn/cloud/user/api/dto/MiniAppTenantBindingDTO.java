package com.aryn.cloud.user.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 小程序 AppID ↔ 租户绑定校验结果（最小公开契约）。
 *
 * 只携带身份与就绪标志：不返回 secret、手机号、微信凭证；
 * accountId/configFingerprint 仅供内部日志排查，前端公开端点不得透出。
 *
 * @author aryn
 * @since 2026-10-10
 */
@Data
public class MiniAppTenantBindingDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String appId;

	private String tenantId;

	private String accountId;

	/**
	 * 配置指纹（updateTime/主键派生），用于内部一致性比较，不参与对外展示。
	 */
	private String configFingerprint;

	private Boolean ready;

}
