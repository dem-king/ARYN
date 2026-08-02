package com.aryn.cloud.upms.api.remote;

/**
 * 员工微信绑定远程查询接口。
 */
public interface RemoteWechatBindingService {

	/**
	 * 根据租户ID和员工ID获取有效绑定的 openid。
	 * @return openid；未绑定返回 null
	 */
	String getOpenid(String tenantId, String userId);
}