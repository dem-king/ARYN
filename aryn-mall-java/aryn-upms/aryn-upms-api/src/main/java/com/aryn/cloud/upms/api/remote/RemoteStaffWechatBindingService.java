package com.aryn.cloud.upms.api.remote;

import com.aryn.cloud.upms.api.entity.SysUserWechatBinding;

/** 配送员工微信绑定查询契约。 */
public interface RemoteStaffWechatBindingService {

	SysUserWechatBinding bind(String tenantId, String userId, String appId, String openId);

	String getBoundOpenId(String tenantId, String userId, String appId);

}
